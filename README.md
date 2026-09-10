# Spring Boot API Observability Practice

## Why This Project Exists

This project is a small Spring Boot practice project for understanding request-level observability.

Monitoring answers "Is the server healthy overall?"

Observability answers "What happened to this one request?"

The main point is learning this flow:

```text
요청 들어옴
→ Interceptor preHandle
→ traceId 생성
→ 시작 시간 기록
→ Controller
→ Service
→ Response DTO 또는 ErrorResponse
→ Interceptor afterCompletion
→ 처리 시간 로그
```

## Study Goal

Spring Boot REST API에서 요청 하나마다 `traceId`를 부여하고, 요청 시작/종료 로그와 처리 시간을 남기는 구조를 학습한다.

성공 응답과 실패 응답 모두 같은 `traceId`를 공유하게 하여, PM/운영자/개발자가 특정 요청 하나를 추적할 수 있는 구조를 이해한다.

이 프로젝트는 단순 monitoring이 아니라 observability의 3대축을 한 요청 단위로 연결하는 것을 목표로 한다.

```text
Metrics = 숫자로 상태를 본다. 요청 수, 처리 시간, 에러 수.
Logs    = 사건 기록을 본다. traceId가 붙은 request start/end 로그.
Traces  = 요청 하나를 따라간다. X-Trace-Id로 응답, 로그, metric 상황을 연결한다.
```

Monitoring은 "문제가 있다"를 알려주고, observability는 "왜 문제가 생겼는지"를 추론하게 해준다.

## Mermaid Flowchart

```mermaid
flowchart TB
    A["Client"] --> B["HTTP Request"]
    B --> C["TraceIdInterceptor"]
    C --> D["preHandle"]
    D --> E["Create traceId"]
    E --> F["Save startTime"]
    F --> G["Controller"]
    G --> H["Service"]
    H --> I["Response"]
    H --> J["Exception"]
    J --> K["ApiExceptionHandler"]
    K --> L["ErrorResponse"]
    I --> M["afterCompletion"]
    L --> M
    M --> N["elapsedMs log"]
```

## Tech Focus

- Java 21
- Virtual Thread
- Spring Boot REST API
- Actuator
- Micrometer
- Prometheus endpoint
- `HandlerInterceptor`
- `preHandle`
- `afterCompletion`
- `traceId`
- `ThreadLocal`
- request logging
- elapsed time
- ErrorResponse traceId
- custom metrics
- metrics / logs / traces correlation

## Observability Axes

### 1. Metrics

Metrics are exposed through Actuator and Prometheus format.

```http
GET /actuator/metrics/practice.api.requests
GET /actuator/metrics/practice.api.request.duration
GET /actuator/metrics/practice.api.errors
GET /actuator/prometheus
```

Custom metrics:

```text
practice.api.requests
= traced API request count

practice.api.request.duration
= traced API request elapsed time

practice.api.errors
= traced API error response count
```

### 2. Logs

Every `/api/**` request writes start/end logs with the same traceId.

```text
request start traceId=demo-trace-001 method=POST uri=/api/chat thread=...
request end traceId=demo-trace-001 method=POST uri=/api/chat status=200 outcome=SUCCESS elapsedMs=...
```

The logging pattern also prints the MDC traceId:

```text
INFO [traceId=demo-trace-001] ...
```

### 3. Traces

This project uses a lightweight manual traceId practice.

```text
Client sends X-Trace-Id
→ TraceIdInterceptor stores it in TraceContext and MDC
→ Controller/Service can read the same traceId
→ Response header and response body include the traceId
→ Logs contain the same traceId
```

This is not a full distributed tracing system yet. Later, this can be extended with Micrometer Tracing, OpenTelemetry, Zipkin, Jaeger, or Grafana Tempo.

## Core Files

### `TraceIdInterceptor.java`

This is the center of the project.

```java
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
```

Before the controller runs, this method creates or receives a `traceId`.

```java
public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
```

After request processing ends, this method logs status code and elapsed time.

It also records request metrics:

```text
practice.api.requests
practice.api.request.duration
practice.api.errors
```

### `TraceContext.java`

This class stores the current request's `traceId`.

```java
private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
```

`ThreadLocal` keeps data attached to the current request-handling thread.

### `WebMvcConfig.java`

This class registers the interceptor.

```java
registry.addInterceptor(traceIdInterceptor)
        .addPathPatterns("/api/**");
```

Only `/api/**` requests are traced.

### `ChatService.java`

The response includes the current traceId.

```java
TraceContext.currentTraceId()
```

This proves that the traceId created in the interceptor can be read inside service logic.

### `ApiExceptionHandler.java`

Validation errors also include the traceId.

```java
return new ErrorResponse(
        "INVALID_REQUEST",
        "Request validation failed",
        HttpStatus.BAD_REQUEST.value(),
        request.getRequestURI(),
        TraceContext.currentTraceId(),
        fieldErrors,
        Instant.now()
);
```

This means success and failure responses can be connected to the same request log.

## Main APIs

### Observability Guide API

```http
GET /api/observability/guide
X-Trace-Id: demo-trace-guide-001
```

This endpoint returns how to check metrics, logs, and traces in this project.

### Chat API

```http
POST /api/chat
Content-Type: application/json
X-Trace-Id: demo-trace-001
```

Request:

```json
{
  "userId": "u01",
  "message": "observability practice",
  "model": "gemini"
}
```

Response:

```json
{
  "userId": "u01",
  "model": "gemini",
  "answer": "u01님의 요청을 gemini 모델로 처리했습니다. message = observability practice",
  "thread": "VirtualThread[...]",
  "traceId": "demo-trace-001"
}
```

### Validation Error

Invalid request:

```json
{
  "userId": "",
  "message": "",
  "model": ""
}
```

Error response:

```json
{
  "code": "INVALID_REQUEST",
  "message": "Request validation failed",
  "status": 400,
  "path": "/api/chat",
  "traceId": "demo-trace-001",
  "fieldErrors": {
    "userId": "userId is required",
    "message": "message is required",
    "model": "model is required"
  },
  "timestamp": "2026-09-05T..."
}
```

## How To Run

```powershell
./gradlew bootRun
```

Send a request with traceId:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Headers @{"X-Trace-Id"="demo-trace-001"} -Body '{"userId":"u01","message":"observability practice","model":"gemini"}'
```

Send a validation error request with traceId:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/chat" -Method Post -ContentType "application/json" -Headers @{"X-Trace-Id"="demo-trace-002"} -Body '{"userId":"","message":"","model":""}'
```

Check the console log:

```text
request start traceId=demo-trace-001 method=POST uri=/api/chat thread=...
request end traceId=demo-trace-001 method=POST uri=/api/chat status=200 elapsedMs=... thread=...
```

Check metrics:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/practice.api.requests" -Method Get
Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/practice.api.request.duration" -Method Get
Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/practice.api.errors" -Method Get
```

Check Prometheus output:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/prometheus" -Method Get
```

Look for:

```text
practice_api_requests_total
practice_api_request_duration_seconds
practice_api_errors_total
```

## PM Study Notes

Observability is important because real incidents are not solved by knowing only that the server failed.

PMs and architects should ask:

- Can one request be traced from start to finish?
- Does the response include a traceId?
- Does the error response include the same traceId?
- Can logs show method, URI, status, and elapsed time?
- Can this traceId later connect to monitoring, alert, and incident reports?

## One-Line Summary

This project practices API observability by connecting metrics, logs, and traceId-based traces so one request can be diagnosed from response to console logs and Actuator metrics.
