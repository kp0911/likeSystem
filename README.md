# Like System

Spring Boot based like processing example with synchronous database locking and asynchronous Redis/RabbitMQ processing.

## Requirements

- Java 21
- Docker Desktop
- k6 for load testing
- Gradle wrapper included in this repository

## Profiles

- `local`: default profile. Uses MariaDB, Redis, and RabbitMQ from `docker-compose.yml`.
- `test`: uses an in-memory H2 database and disables Rabbit listener auto startup for fast local tests.
- `scenario-a`: database-focused scenario configuration.
- `scenario-b`: Redis/RabbitMQ-focused scenario configuration.

`spring.profiles.default=local` is set in `application.properties`, so normal startup uses the local Docker services unless another profile is specified.

## Start From Scratch

Open Docker Desktop first, then run these commands from the project root.

```powershell
cd C:\Users\kp096\IdeaProjects\likeSystem
docker compose up -d
docker compose ps
```

Expected containers:

- `mariadb-container`
- `redis-container`
- `rabbitmq-container`

RabbitMQ management UI:

```text
http://localhost:15672
username: guest
password: guest
```

If Java is not on PATH, set `JAVA_HOME` for the current PowerShell session:

```powershell
$env:JAVA_HOME='C:\Users\kp096\.jdks\ms-21.0.9'
```

Run compile and unit tests before starting the app:

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test
```

Start the application:

```powershell
.\gradlew.bat bootRun
```

Startup is healthy when the app stays running on port `8080`, MariaDB connects, and the RabbitMQ listener starts without error logs.

## Manual API Test

Synchronous like processing:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/like/sync `
  -ContentType 'application/json' `
  -Body '{"videoId":1}'
```

Asynchronous like processing:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/like/async `
  -ContentType 'application/json' `
  -Body '{"videoId":1,"userId":"manual-user-1"}'
```

Buffered asynchronous like processing:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/like/buffered-async `
  -ContentType 'application/json' `
  -Body '{"videoId":1,"userId":"manual-buffered-user-1"}'
```

Check the database value:

```powershell
docker exec -it mariadb-container mariadb -u db_user -pdb_password like_system -e "select * from video;"
```

Test dashboard:

```text
http://localhost:8080/like-test-dashboard.html
```

This dashboard shows request count, success count, failure count, average response time, Redis counts, RabbitMQ queue depth, database count, and step-by-step flow metrics for `sync`, `async-event`, and `buffered-async`.

The dashboard can also start k6 load tests through the Spring Boot server. This is intended only for local study and portfolio demonstrations. The server runs only the fixed scripts mapped to `sync`, `async-event`, and `buffered-async`; arbitrary shell commands are not accepted.

## Reset Test State

Run this after the app has started and RabbitMQ has declared `like.queue`.

```powershell
.\scripts\reset-test-state.ps1
```

This resets `video.id = 1`, clears Redis keys, and purges `like.queue`.

## Performance Comparison With k6

Two k6 scripts are provided so the endpoint does not need to be edited between runs.

Synchronous endpoint:

```powershell
k6 run load-test-sync.js
```

Asynchronous endpoint:

```powershell
k6 run load-test-async.js
```

Buffered asynchronous endpoint:

```powershell
k6 run load-test-buffered-async.js
```

Use the same test sequence for a fair comparison:

1. Start Docker containers.
2. Start the Spring Boot app.
3. Run `.\scripts\reset-test-state.ps1`.
4. Run `k6 run load-test-sync.js`.
5. Check `video.like_count`.
6. Run `.\scripts\reset-test-state.ps1`.
7. Run `k6 run load-test-async.js`.
8. Watch RabbitMQ `like.queue` until `Ready` returns to `0`.
9. Check `video.like_count` again.
10. Run `.\scripts\reset-test-state.ps1`.
11. Run `k6 run load-test-buffered-async.js`.
12. Watch RabbitMQ `like.aggregate.queue` until `Ready` returns to `0`.
13. Check `video.like_count` again.

Important k6 metrics:

- `http_reqs`: total processed HTTP requests
- `http_req_duration avg`: average response time
- `http_req_duration p(95)`: response time for the slowest 5 percent boundary
- `http_req_failed`: failed request rate
- `checks`: HTTP 200 success check rate

## How To Judge The Result

For `/sync`, the HTTP response includes the database update path. If `p(95)` grows sharply under load, the database row lock is likely the bottleneck.

For `/async`, the HTTP response only proves Redis duplicate checking and RabbitMQ publishing were fast. Final database reflection happens later in the consumer, so also watch RabbitMQ:

For `/buffered-async`, the HTTP response only updates Redis. A scheduler periodically flushes Redis pending counts to RabbitMQ as aggregate delta events, and the consumer applies `like_count += delta` to the database.

- `Ready`: messages waiting to be consumed
- `Unacked`: messages currently handled by consumers
- `Publish rate`: incoming message rate
- `Ack rate`: completed message rate

Interpretation:

- If `/async` has low `p(95)` and `Ready` returns to `0` shortly after the test, it is handling bursts well.
- If `/async` has low `p(95)` but `Ready` keeps growing, API responses are fast but final database reflection is delayed.
- If `/buffered-async` has low `p(95)` and `like.aggregate.queue` drains quickly, it is reducing RabbitMQ message count and database update count.
- If `/sync` has high `p(95)` but the database count is immediately accurate, it favors consistency over response speed.

Check final DB count:

```powershell
docker exec -it mariadb-container mariadb -u db_user -pdb_password like_system -e "select id, like_count from video where id = 1;"
```
