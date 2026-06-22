# Docker 실행 오류 대응 가이드

이 문서는 다른 환경에서 Like System 프로젝트를 clone/pull 받은 뒤 Docker로 실행할 때 발생할 수 있는 오류와 해결 방법을 정리한다.

기본 실행 명령은 아래와 같다.

```powershell
docker compose up --build -d
docker compose ps
```

정상 실행 기준:

- `like-system-app` 상태가 `Up`
- `mariadb-container` 상태가 `Up` 또는 `healthy`
- `redis-container` 상태가 `Up` 또는 `healthy`
- `rabbitmq-container` 상태가 `Up` 또는 `healthy`
- 대시보드 접속 가능: `http://localhost:8080/like-test-dashboard.html`

## 1. Docker가 실행되지 않은 경우

### 증상

```text
Cannot connect to the Docker daemon
error during connect
```

### 원인

Docker Desktop 또는 Docker Engine이 실행 중이 아니다.

### 해결

Docker Desktop을 먼저 실행한 뒤 다시 명령을 실행한다.

```powershell
docker version
docker compose version
docker compose up --build -d
```

`docker version`에서 Server 정보가 나오면 Docker 데몬이 정상 실행 중인 것이다.

## 2. 포트 충돌

### 증상

```text
Bind for 0.0.0.0:8080 failed: port is already allocated
Bind for 0.0.0.0:3306 failed: port is already allocated
Bind for 0.0.0.0:6379 failed: port is already allocated
Bind for 0.0.0.0:5672 failed: port is already allocated
Bind for 0.0.0.0:15672 failed: port is already allocated
```

### 원인

다른 프로세스나 기존 컨테이너가 같은 포트를 사용 중이다.

현재 프로젝트가 사용하는 포트:

| 포트 | 용도 |
|---:|---|
| `8080` | Spring Boot app |
| `3306` | MariaDB |
| `6379` | Redis |
| `5672` | RabbitMQ AMQP |
| `15672` | RabbitMQ Management UI |

### 확인

```powershell
docker ps
netstat -ano | findstr :8080
netstat -ano | findstr :3306
netstat -ano | findstr :6379
netstat -ano | findstr :5672
netstat -ano | findstr :15672
```

### 해결 방법 A: 기존 컨테이너 종료

동일 프로젝트의 기존 컨테이너가 남아 있다면 아래처럼 정리한다.

```powershell
docker compose down
docker compose up --build -d
```

다른 Compose 프로젝트 컨테이너가 포트를 쓰고 있다면 해당 프로젝트에서 종료한다.

```powershell
docker ps
docker stop <container_id>
```

### 해결 방법 B: 포트 변경

다른 DB나 Redis를 계속 실행해야 한다면 `docker-compose.yml`의 왼쪽 포트를 변경한다.

예시:

```yaml
ports:
  - "18080:8080"
```

이 경우 대시보드 접속 URL도 변경된다.

```text
http://localhost:18080/like-test-dashboard.html
```

컨테이너 내부 통신은 service name을 사용하므로 오른쪽 포트는 바꾸지 않는다.

## 3. Docker 이미지 다운로드 실패

### 증상

```text
failed to resolve source metadata
pull access denied
i/o timeout
TLS handshake timeout
```

### 원인

Docker Hub 또는 외부 네트워크 접근이 막혀 있다.

이 프로젝트는 아래 이미지를 내려받는다.

- `eclipse-temurin:21-jdk`
- `eclipse-temurin:21-jre`
- `grafana/k6:0.54.0`
- `mariadb:10.11`
- `redis:7.2`
- `rabbitmq:3.13-management`

### 확인

```powershell
docker pull eclipse-temurin:21-jdk
docker pull mariadb:10.11
```

### 해결

- 인터넷 연결 확인
- 회사/학교 네트워크라면 프록시 또는 방화벽 확인
- Docker Desktop 로그인 확인
- VPN 사용 중이면 끄거나 다른 네트워크에서 재시도

Docker Hub rate limit이 의심되면 Docker Desktop에서 로그인 후 다시 시도한다.

```powershell
docker login
docker compose up --build -d
```

## 4. Gradle 빌드 실패

### 증상

```text
Could not resolve all files for configuration
Could not find org.springframework.boot
repo.spring.io
services.gradle.org
```

### 원인

Docker build 중 Gradle wrapper와 의존성을 내려받아야 하는데 네트워크가 막혔거나 저장소 접근이 실패했다.

현재 프로젝트는 Spring Boot `3.5.15-SNAPSHOT`을 사용하므로 `repo.spring.io/snapshot` 접근이 필요하다.

### 확인

```powershell
docker compose build app
```

빌드 로그에서 실패한 URL을 확인한다.

### 해결

- 네트워크 접근 확인
- 프록시 환경이면 Docker Desktop proxy 설정 확인
- 일시적인 저장소 장애일 수 있으므로 재시도

```powershell
docker compose build --no-cache app
docker compose up -d
```

장기적으로 안정성을 높이려면 snapshot 버전 대신 정식 릴리스 버전의 Spring Boot로 변경하는 것이 좋다.

## 5. app 컨테이너가 바로 종료되는 경우

### 증상

```text
like-system-app Exited
```

### 확인

```powershell
docker compose ps
docker compose logs app
```

### 주요 원인

- MariaDB 연결 실패
- Redis 연결 실패
- RabbitMQ 연결 실패
- profile 설정 오류
- Gradle 빌드 산출물 문제

### 해결

먼저 인프라 컨테이너 상태를 확인한다.

```powershell
docker compose ps
docker compose logs mariadb
docker compose logs redis
docker compose logs rabbitmq
```

`application-docker.yml`에서는 Docker Compose service name을 사용한다.

| 대상 | host |
|---|---|
| MariaDB | `mariadb` |
| Redis | `redis` |
| RabbitMQ | `rabbitmq` |

app 컨테이너의 profile도 확인한다.

```powershell
docker compose exec app printenv SPRING_PROFILES_ACTIVE
```

정상값:

```text
docker
```

## 6. MariaDB healthcheck 실패

### 증상

```text
mariadb-container unhealthy
dependency failed to start
```

### 원인

MariaDB 초기화가 늦거나, 기존 데이터 볼륨/컨테이너 상태가 꼬였을 수 있다.

### 확인

```powershell
docker compose logs mariadb
docker compose ps
```

### 해결

먼저 조금 기다린 뒤 상태를 다시 확인한다.

```powershell
docker compose ps
```

계속 실패하면 컨테이너를 재생성한다.

```powershell
docker compose down
docker compose up --build -d
```

현재 compose 파일에는 named volume을 사용하지 않으므로 `docker compose down` 후 재기동하면 컨테이너 내부 데이터는 초기화된다.

## 7. RabbitMQ healthcheck 실패

### 증상

```text
rabbitmq-container unhealthy
like-system-app does not start
```

### 원인

RabbitMQ가 아직 완전히 준비되지 않았거나, 5672 포트가 열리지 않았다.

### 확인

```powershell
docker compose logs rabbitmq
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
docker compose exec rabbitmq rabbitmq-diagnostics -q check_port_connectivity
```

### 해결

컨테이너를 재시작한다.

```powershell
docker compose restart rabbitmq
docker compose up -d app
```

RabbitMQ 관리 화면 접속:

```text
http://localhost:15672
ID: guest
PW: guest
```

## 8. Redis healthcheck 실패

### 증상

```text
redis-container unhealthy
RedisConnectionFailureException
```

### 원인

Redis 컨테이너가 시작되지 않았거나 포트 충돌/메모리 설정 문제가 있다.

### 확인

```powershell
docker compose logs redis
docker compose exec redis redis-cli ping
```

정상 응답:

```text
PONG
```

### 해결

```powershell
docker compose restart redis
docker compose up -d app
```

## 9. 대시보드 접속 실패

### 증상

```text
http://localhost:8080/like-test-dashboard.html 접속 실패
404 Not Found
Connection refused
```

### 원인

- app 컨테이너가 아직 시작 중
- app 컨테이너가 종료됨
- 8080 포트가 다른 서비스에 연결됨
- URL 오타

### 확인

```powershell
docker compose ps
docker compose logs app
```

정상 로그 예시:

```text
Tomcat started on port 8080
Started LikeSystemApplication
```

### 해결

app이 아직 시작 중이면 잠시 기다린다. 종료된 상태라면 로그를 보고 원인을 해결한 뒤 재기동한다.

```powershell
docker compose up -d app
```

## 10. 대시보드에서 k6 실행 실패

### 증상

```text
k6 실행 실패: PATH 또는 설치 상태를 확인하세요.
```

### 원인

app 컨테이너 안에서 `k6` 실행 파일을 찾지 못했다.

현재 Dockerfile은 `grafana/k6:0.54.0` 이미지에서 `k6` 바이너리를 복사하므로, 최신 이미지를 빌드하지 않은 경우 발생할 수 있다.

### 확인

```powershell
docker compose exec app k6 version
```

정상 예시:

```text
k6 v0.54.0
```

### 해결

이미지를 다시 빌드한다.

```powershell
docker compose up --build -d app
docker compose exec app k6 version
```

## 11. API 호출 시 404 또는 400 발생

### 주요 API

| 기능 | Method | URL |
|---|---|---|
| sync 좋아요 | `POST` | `/api/v1/like/sync` |
| buffered async 좋아요 | `POST` | `/api/v1/like/buffered-async` |
| 시스템 상태 조회 | `GET` | `/api/v1/like/system-state?videoId=1` |
| 테스트 상태 초기화 | `POST` | `/api/v1/like/test-state/reset?videoId=1` |
| k6 시작 | `POST` | `/api/v1/load-tests/start` |
| k6 중지 | `POST` | `/api/v1/load-tests/stop` |

### 400 원인

요청 본문이 잘못되었거나 필수 값이 없다.

정상 요청:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/like/sync `
  -ContentType 'application/json' `
  -Body '{"videoId":1}'
```

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/like/buffered-async `
  -ContentType 'application/json' `
  -Body '{"videoId":1,"userId":"manual-user-1"}'
```

## 12. API 호출 시 500 발생

### 확인

```powershell
docker compose logs app --tail=200
```

### 자주 보는 원인

- DB에 `video.id = 1` 데이터가 없음
- MariaDB 연결 실패
- Redis 연결 실패
- RabbitMQ 연결 실패

### 해결

대시보드의 `DB/Redis/Queue 초기화` 버튼을 누르거나, 직접 DB를 확인한다.

```powershell
docker exec -it mariadb-container mariadb -u db_user -pdb_password like_system -e "select * from video;"
```

`video.id = 1`이 없다면 아래 명령으로 생성한다.

```powershell
docker exec -it mariadb-container mariadb -u db_user -pdb_password like_system -e "insert into video (id, like_count) values (1, 0) on duplicate key update like_count = like_count;"
```

## 13. buffered-async 결과가 DB에 바로 안 보이는 경우

### 증상

`/api/v1/like/buffered-async` 호출은 성공했지만 DB `like_count`가 즉시 증가하지 않는다.

### 원인

정상 동작일 수 있다. buffered-async는 Redis pending count에 누적한 뒤 scheduler가 RabbitMQ로 delta 이벤트를 발행하고, consumer가 DB에 반영한다.

### 확인

대시보드에서 아래 값을 확인한다.

- `Redis display count`
- `Redis pending count`
- `RabbitMQ 대기 메시지`
- `DB like_count`

또는 직접 확인한다.

```powershell
docker exec -it mariadb-container mariadb -u db_user -pdb_password like_system -e "select id, like_count from video where id = 1;"
```

RabbitMQ queue 확인:

```text
http://localhost:15672
ID: guest
PW: guest
```

`like.aggregate.queue`의 `Ready`가 계속 쌓이면 consumer 처리량보다 유입량이 많거나 consumer가 실패 중일 수 있다.

```powershell
docker compose logs app --tail=200
```

## 14. 초기화가 되지 않는 경우

### 증상

대시보드에서 `DB/Redis/Queue 초기화`를 눌러도 값이 그대로 보인다.

### 원인

- app이 RabbitMQ queue를 아직 선언하지 않음
- Redis나 RabbitMQ 연결 실패
- 브라우저 캐시 또는 갱신 지연

### 확인

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/v1/like/test-state/reset?videoId=1
```

응답 예시:

```text
videoId updatedDatabaseRows deletedRedisKeys purgedQueue
1       1                   3                like.aggregate.queue
```

### 해결

app이 완전히 뜬 뒤 다시 실행한다.

```powershell
docker compose logs app --tail=100
```

`Started LikeSystemApplication` 로그를 확인한 뒤 초기화한다.

## 15. 컨테이너 이름 충돌

### 증상

```text
Conflict. The container name "/like-system-app" is already in use
```

### 원인

같은 이름의 컨테이너가 이미 존재한다.

### 해결

기존 프로젝트 컨테이너를 종료하고 제거한다.

```powershell
docker compose down
docker compose up --build -d
```

그래도 남아 있으면 컨테이너를 확인한다.

```powershell
docker ps -a
docker rm <container_id>
```

## 16. Windows PowerShell 명령이 동작하지 않는 경우

### 증상

PowerShell에서 줄바꿈 명령이 깨지거나 `Invoke-RestMethod` JSON body가 인식되지 않는다.

### 해결

한 줄 명령으로 실행한다.

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/like/sync -ContentType 'application/json' -Body '{"videoId":1}'
```

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/like/buffered-async -ContentType 'application/json' -Body '{"videoId":1,"userId":"manual-user-1"}'
```

## 17. 전체 재시작 절차

원인을 찾기 어렵다면 아래 순서로 재시작한다.

```powershell
docker compose down
docker compose up --build -d
docker compose ps
docker compose logs app --tail=100
```

대시보드 접속:

```text
http://localhost:8080/like-test-dashboard.html
```

초기화:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/like/test-state/reset?videoId=1
```

## 18. 진단용 명령 모음

컨테이너 상태:

```powershell
docker compose ps
```

전체 로그:

```powershell
docker compose logs --tail=200
```

app 로그:

```powershell
docker compose logs app --tail=200
```

DB 확인:

```powershell
docker exec -it mariadb-container mariadb -u db_user -pdb_password like_system -e "select id, like_count from video;"
```

Redis 확인:

```powershell
docker exec -it redis-container redis-cli keys "like:buffered:*"
```

RabbitMQ queue 확인:

```powershell
docker exec -it rabbitmq-container rabbitmqctl list_queues
```

k6 확인:

```powershell
docker compose exec app k6 version
```

Compose 설정 검증:

```powershell
docker compose config
```
