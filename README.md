# JPA N+1 Practice

This branch is a Postman-based JPA N+1 experiment inside the Spring Boot API observability project.

The point is not to repeat generic JPA performance notes. The point is to see the hidden SQL cost behind a normal-looking API response.

```text
Postman can show a successful response.
Hibernate SQL logs show what the server actually did to the DB.
```

## What I Tested

This experiment checks a common JPA performance problem:

```text
The API response can look normal,
but Hibernate may be running repeated SELECT queries behind the response.
```

The important observation is:

```text
/bad
→ the response body looks normal
→ Hibernate first loads chat logs
→ then LAZY user access triggers repeated user SELECT queries

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
