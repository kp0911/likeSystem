# Like System

Spring Boot based like processing example comparing direct database updates and Redis-buffered asynchronous aggregation.

## Requirements

- Docker Desktop
- k6 for host-side load testing. The Docker app image already includes k6 for dashboard-triggered tests.

## Profiles

- `local`: uses MariaDB, Redis, and RabbitMQ on localhost.
- `docker`: used by the app container. Uses Docker Compose service names.
- `test`: uses an in-memory H2 database and disables Rabbit listener auto startup for fast local tests.

`spring.profiles.default=local` is set in `application.properties`, so normal startup uses the local Docker services unless another profile is specified.

## Start From Scratch

Open Docker Desktop first, then run the full stack from the project root.

```powershell
cd C:\Users\kp096\IdeaProjects\likeSystem
docker compose up --build -d
docker compose ps
```

Expected containers:

- `like-system-app`
- `mariadb-container`
- `redis-container`
- `rabbitmq-container`

RabbitMQ management UI:

```text
http://localhost:15672
username: guest
password: guest
```

Startup is healthy when `like-system-app` exposes port `8080`, MariaDB connects, and the RabbitMQ listener starts without error logs.

## Manual API Test

Synchronous like processing:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/like/sync `
  -ContentType 'application/json' `
  -Body '{"videoId":1}'
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

This dashboard shows request count, success count, failure count, average response time, Redis counts, RabbitMQ queue depth, database count, and step-by-step flow metrics for `sync` and `buffered-async`.

When the project is started with `docker compose up --build -d`, the app container includes k6, so the dashboard can start load tests directly.

If Docker startup fails in another environment, see `docs/docker-troubleshooting.md`.

## Reset Test State

Run this after the app has started and RabbitMQ has declared `like.aggregate.queue`.

```powershell
.\scripts\reset-test-state.ps1
```

This resets `video.id = 1`, clears Redis keys, and purges `like.aggregate.queue`.

## Performance Comparison With k6

Two k6 scripts are provided so the endpoint does not need to be edited between runs.

Synchronous endpoint:

```powershell
k6 run load-test-sync.js
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
7. Run `k6 run load-test-buffered-async.js`.
8. Watch RabbitMQ `like.aggregate.queue` until `Ready` returns to `0`.
9. Check `video.like_count` again.

Important k6 metrics:

- `http_reqs`: total processed HTTP requests
- `http_req_duration avg`: average response time
- `http_req_duration p(95)`: response time for the slowest 5 percent boundary
- `http_req_failed`: failed request rate
- `checks`: HTTP 200 success check rate

## How To Judge The Result

For `/sync`, the HTTP response includes one atomic database update: `like_count = like_count + 1`. It avoids lost updates, but every request still waits for a database write.

For `/buffered-async`, the HTTP response validates the video and atomically updates Redis. A scheduler moves pending counts to a Redis outbox, publishes aggregate delta events only after RabbitMQ broker confirmation, and the consumer applies `like_count += delta` to the database once per event ID.

- `Ready`: messages waiting to be consumed
- `Unacked`: messages currently handled by consumers
- `Publish rate`: incoming message rate
- `Ack rate`: completed message rate

Interpretation:

- If `/buffered-async` has low `p(95)` and `like.aggregate.queue` drains quickly, it is reducing RabbitMQ message count and database update count.
- If `/sync` has high `p(95)`, direct DB update is showing its database write bottleneck. Its atomic update query should keep the final DB count aligned with successful requests.

Check final DB count:

```powershell
docker exec -it mariadb-container mariadb -u db_user -pdb_password like_system -e "select id, like_count from video where id = 1;"
```
