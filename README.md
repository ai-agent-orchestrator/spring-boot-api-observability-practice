# JPA Transaction Practice

This branch is a Postman-based JPA experiment inside the Spring Boot API observability project.

The point is not to repeat generic JPA notes. The point is to prove one practical mistake:

```text
Changing an Entity object in Java is not the same as updating the DB row.
```

## First Observability Moment

The first meaningful moment in this experiment was not just getting a successful Postman response.

It was finding the same traceId in the server log:

```text
INFO [traceId=jpa-practice-create-001]
```

and then seeing Hibernate execute the actual INSERT SQL:

```sql
Hibernate:
    insert
    into
        practice_menu
```

This connected the whole backend flow:

```text
Postman Header
→ X-Trace-Id: jpa-practice-create-001

Spring Boot Log
→ traceId=jpa-practice-create-001

Hibernate SQL Log
→ insert into practice_menu
```

At that point, the request was no longer abstract.

I could see that one Postman request reached the Spring Boot server, passed through the traceId logging flow, and actually created a database row through Hibernate.

This is the backend feedback loop I want to keep practicing:

```text
Postman response
→ traceId log
→ Hibernate SQL log
→ actual DB behavior
```

## What I Tested

This experiment checks the difference between changing an Entity object in memory and actually updating the database row.

The important observation is:

```text
/bad
→ the response body shows the changed value
→ but the next GET request shows the original DB value
→ Hibernate SQL log shows SELECT only, no UPDATE

/good
→ the response body shows the changed value
→ the next GET request also shows the changed DB value
→ Hibernate SQL log shows SELECT + UPDATE
```

This proves that a changed Java object is not enough.

The Entity must be managed inside a transaction for Dirty Checking to update the DB.

## Practice APIs

```text
POST  /api/transaction-practice/menus
GET   /api/transaction-practice/menus/{id}
PATCH /api/transaction-practice/menus/{id}/bad
PATCH /api/transaction-practice/menus/{id}/good
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

Create the first menu:

```http
POST http://localhost:8080/api/transaction-practice/menus
Content-Type: application/json
X-Trace-Id: jpa-practice-create-001
```

Body:

```json
{
  "name": "original-menu",
  "price": 1000
}
```

Expected response:

```json
{
  "id": 1,
  "name": "original-menu",
  "price": 1000,
  "message": "created by repository.save()",
  "traceId": "jpa-practice-create-001"
}
```

## What Postman Showed

### 1. Bad Update

Request:

```http
PATCH http://localhost:8080/api/transaction-practice/menus/1/bad
Content-Type: application/json
X-Trace-Id: jpa-practice-bad-001
```

Body:

```json
{
  "name": "bad-change"
}
```

Response:

```json
{
  "id": 1,
  "name": "bad-change",
  "price": 1000,
  "message": "BAD: response object changed, but DB will not be updated by Dirty Checking.",
  "traceId": "jpa-practice-bad-001"
}
```

At this point, the API response looked changed.

But the follow-up request told the real result:

```http
GET http://localhost:8080/api/transaction-practice/menus/1
X-Trace-Id: jpa-practice-get-after-bad-001
```

Response:

```json
{
  "id": 1,
  "name": "original-menu",
  "price": 1000,
  "message": "current database value",
  "traceId": "jpa-practice-get-after-bad-001"
}
```

Observation:

```text
The response object changed,
but the DB value did not change.
```

Hibernate SQL log:

```sql
SELECT only
No UPDATE
```

### 2. Good Update

Request:

```http
PATCH http://localhost:8080/api/transaction-practice/menus/1/good
Content-Type: application/json
X-Trace-Id: jpa-practice-good-001
```

Body:

```json
{
  "name": "good-change"
}
```

Response:

```json
{
  "id": 1,
  "name": "good-change",
  "price": 1000,
  "message": "GOOD: changed inside @Transactional. Dirty Checking will update DB.",
  "traceId": "jpa-practice-good-001"
}
```

Follow-up request:

```http
GET http://localhost:8080/api/transaction-practice/menus/1
X-Trace-Id: jpa-practice-get-after-good-001
```

Response:

```json
{
  "id": 1,
  "name": "good-change",
  "price": 1000,
  "message": "current database value",
  "traceId": "jpa-practice-get-after-good-001"
}
```

Observation:

```text
The response changed,
and the DB value also changed.
```

Hibernate SQL log:

```sql
SELECT
UPDATE
```

## Result

```text
Postman showed the API response.
Hibernate SQL logs showed what actually happened inside the server.
The follow-up GET request confirmed whether the DB row really changed.
```

The practical lesson:

```text
Changing an Entity object is not the same as updating the DB.
Dirty Checking works when the Entity is managed inside a Service-level @Transactional boundary.
```

## Why This Matters

This is part of the backend feedback loop:

```text
Postman response
→ traceId logs
→ Hibernate SQL logs
→ follow-up GET
→ actual DB behavior
```

The goal is to understand JPA by observing real request/response behavior, not by memorizing isolated annotations.
