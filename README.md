# Spring Boot Observability Practice

This project is a custom observability practice for Spring Boot APIs.

```text
Spring Boot Observability Practice
-> from JPA N+1 metrics
-> to AI Agent behavior metrics
-> to Security/Guardrail metrics
```

The goal is to observe internal cost and behavior behind successful API responses.

## JPA N+1 Practice

This branch is a Postman-based JPA N+1 experiment inside the Spring Boot API observability project.

## Actual Experiment Result

This is the key flow observed in this experiment:

```text
1. ChatLog list is queried first.
   select * from practice_chat_log

2. Five ChatLog objects are loaded into memory.
   But user is LAZY, so PracticeUser is not loaded yet.

3. During DTO conversion, userName is needed.
   chatLog.getUser().getName()

4. At that moment, Hibernate loads each ChatLog's User from DB one by one.
   select * from practice_user where id=?
   select * from practice_user where id=?
   select * from practice_user where id=?
   ...
```

In this experiment:

```text
ChatLog list SELECT 1 time
+ User SELECT N times
= N+1
```

So the problem is not that the Postman response is broken. The response can look perfectly normal.

The problem is hidden in the Hibernate SQL log.

The point is not to repeat generic JPA performance notes. The point is to see the hidden SQL cost behind a normal-looking API response.

## What I Tested

This experiment checks this exact situation:

```text
/bad
→ the response body looks normal
→ Hibernate first loads chat logs
→ then LAZY user access triggers repeated user SELECT queries
→ ChatLog list SELECT 1 time + User SELECT N times

/good
→ the response body looks similar
→ join fetch loads chat logs and users together
→ repeated user SELECT queries are reduced
```

N+1 is dangerous because the API can be functionally correct while SQL quietly explodes.

## Practice APIs

```text
POST /api/n-plus-one-practice/sample-data
GET  /api/n-plus-one-practice/bad
GET  /api/n-plus-one-practice/good
```

## Postman Setup

Run the server:

```powershell
./gradlew bootRun
```

Health check:

```http
GET http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

## What Postman Should Show

### 1. Create Sample Data

Request:

```http
POST http://localhost:8080/api/n-plus-one-practice/sample-data
X-Trace-Id: n-plus-one-sample-001
```

This creates:

```text
5 PracticeUser rows
5 PracticeChatLog rows
Each chat log points to one user with @ManyToOne(fetch = LAZY)
```

Expected response shape:

```json
{
  "mode": "sample-data",
  "message": "created 5 users and 5 chat logs",
  "count": 5,
  "traceId": "n-plus-one-sample-001",
  "chatLogs": [
    {
      "chatLogId": 1,
      "message": "message from user-1",
      "userId": 1,
      "userName": "user-1"
    }
  ]
}
```

### 2. Bad Query

Request:

```http
GET http://localhost:8080/api/n-plus-one-practice/bad
X-Trace-Id: n-plus-one-bad-001
```

What this does:

```text
1. practiceChatLogRepository.findAll()
2. Response DTO calls chatLog.getUser().getName()
3. LAZY user is accessed one by one
4. Hibernate can run repeated SELECT queries
```

Expected Postman result:

```text
The response body looks normal.
It returns chat logs with userName.
```

Expected Hibernate SQL log:

```sql
select ... from practice_chat_log ...
select ... from practice_user where id=?
select ... from practice_user where id=?
select ... from practice_user where id=?
select ... from practice_user where id=?
select ... from practice_user where id=?
```

Key evidence from `/bad`:

```text
/bad
→ API response looks correct
→ repeated SELECT queries appear in Hibernate SQL logs
→ the problem is not the response body
→ the problem is hidden DB query cost
```

### 3. Good Query

Request:

```http
GET http://localhost:8080/api/n-plus-one-practice/good
X-Trace-Id: n-plus-one-good-001
```

What this does:

```text
1. practiceChatLogRepository.findAllWithUser()
2. JPQL join fetch loads PracticeChatLog and PracticeUser together
3. Response DTO can read userName without repeated user SELECT queries
```

Expected Postman result:

```text
The response body looks similar to /bad.
It still returns chat logs with userName.
```

Expected Hibernate SQL log:

```sql
select ...
from practice_chat_log ...
join practice_user ...
```

Key evidence from `/good`:

```text
/good
→ API response looks similar to /bad
→ Hibernate SQL log shows a join query
→ repeated user SELECT queries are reduced
→ fetch join fixed the hidden query cost
```

## Result

```text
Postman confirms the API response.
Hibernate SQL logs reveal the hidden DB query cost.
traceId connects the Postman request to the server log.
```

The practical lesson:

```text
A correct API response does not guarantee an efficient API.
N+1 must be checked through Hibernate SQL logs, not only through Postman response bodies.
```

## Why This Matters

This is part of the backend feedback loop:

```text
Postman response
→ traceId logs
→ Hibernate SQL logs
→ repeated SELECT detection
→ fetch join comparison
```

The goal is to understand JPA performance by observing real request/response behavior and actual SQL execution.

## Prometheus and Grafana Check

This branch also exposes SQL statement count as application metrics.

```text
practice.api.sql.statements
practice.api.sql.statements.per.request
```

Prometheus converts dots to underscores:

```promql
practice_api_sql_statements_total
practice_api_sql_statements_per_request_count
practice_api_sql_statements_per_request_sum
```

Compare `/bad` and `/good` with these queries:

```promql
increase(practice_api_sql_statements_total{uri="/api/n-plus-one-practice/bad"}[5m])
```

```promql
increase(practice_api_sql_statements_total{uri="/api/n-plus-one-practice/good"}[5m])
```

Average SQL statements per request:

```promql
rate(practice_api_sql_statements_per_request_sum{uri="/api/n-plus-one-practice/bad"}[5m])
/
rate(practice_api_sql_statements_per_request_count{uri="/api/n-plus-one-practice/bad"}[5m])
```

```promql
rate(practice_api_sql_statements_per_request_sum{uri="/api/n-plus-one-practice/good"}[5m])
/
rate(practice_api_sql_statements_per_request_count{uri="/api/n-plus-one-practice/good"}[5m])
```

The point is:

```text
/bad  -> one HTTP request, many SQL statements
/good -> one HTTP request, fewer SQL statements
```

## Agent Custom Metrics Practice

This branch also starts a guardrail-ready agent observability practice.

NeMo Guardrails is not integrated yet. The goal is to define a replaceable boundary first:

```text
Agent API
-> AgentPracticeService
-> AgentMetricRecorder
-> Prometheus / Grafana
```

Later, a mock policy decision can be replaced with a real NeMo Guardrails adapter.

Practice APIs:

```http
POST /api/agent/practice/run
POST /api/agent/practice/tool-error
POST /api/agent/practice/retry
POST /api/agent/practice/approval
POST /api/agent/practice/policy-violation
POST /api/agent/practice/external-api
POST /api/agent/practice/db-write
```

Sample body:

```json
{
  "userInput": "search recent policy documents",
  "toolName": "search",
  "planSteps": 3,
  "retryCount": 1,
  "promptTokens": 120,
  "completionTokens": 80
}
```

Custom metrics:

```text
agent.tool.calls
agent.tool.errors
agent.plan.steps
agent.plan.steps.per.request
agent.retry.count
agent.cost.tokens
agent.external.api.calls
agent.db.write
agent.approval.required
agent.policy.violation
```

Prometheus metric names:

```promql
agent_tool_calls_total
agent_tool_errors_total
agent_plan_steps_total
agent_plan_steps_per_request_sum
agent_retry_count_total
agent_cost_tokens_total
agent_external_api_calls_total
agent_db_write_total
agent_approval_required_total
agent_policy_violation_total
```

PromQL examples:

```promql
increase(agent_tool_calls_total[5m])
```

```promql
increase(agent_tool_errors_total[5m])
```

```promql
increase(agent_retry_count_total[5m])
```

```promql
increase(agent_external_api_calls_total[5m])
```

```promql
increase(agent_db_write_total[5m])
```

```promql
increase(agent_approval_required_total[5m])
```

```promql
increase(agent_policy_violation_total[5m])
```

The point is:

```text
API success is not enough.
Agent behavior, retries, tool failures, token cost, approvals, and policy violations must be observable.
```
