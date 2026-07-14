# 좋아요 처리 시스템 성능 비교 보고서

## 1. 프로젝트 개요

이 프로젝트는 유튜브와 같은 서비스에서 많은 사용자가 동시에 하나의 영상에 좋아요를 누르는 상황을 가정하고, 좋아요 처리 방식에 따라 응답 성능과 DB 부하가 어떻게 달라지는지 확인하기 위해 만들었다.

핵심 질문은 다음과 같다.

```text
좋아요 요청이 발생할 때마다 DB에 직접 update를 실행하면,
대규모 트래픽 상황에서 DB는 어떻게 버틸 수 있는가?
```

최종 비교 대상은 두 가지 방식이다.

| 구분 | API | 처리 방식 | 핵심 특징 |
| --- | --- | --- | --- |
| 동기 처리 | `/api/v1/like/sync` | DB에 직접 원자적 증가 쿼리 실행 | 정합성을 유지하지만 요청마다 DB write가 발생 |
| 버퍼 비동기 처리 | `/api/v1/like/buffered-async` | Redis에 누적 후 RabbitMQ delta 이벤트로 DB 일괄 반영 | 요청 응답이 빠르고 DB update 횟수를 줄일 수 있음 |

기존 개별 이벤트 기반 async 방식은 좋아요 1건당 RabbitMQ 메시지 1개와 DB update 1회를 발생시키므로, “대규모 트래픽에서 DB update 횟수를 줄인다”는 프로젝트 목적과 맞지 않아 제거했다.

## 2. 기술 스택과 구성

| 영역 | 사용 기술 | 역할 |
| --- | --- | --- |
| Application | Java 21, Spring Boot | REST API 및 비즈니스 로직 구현 |
| Database | MariaDB | 최종 좋아요 수 저장 |
| ORM | Spring Data JPA, Hibernate | `Video` 엔티티 저장 및 조회 |
| Cache / Buffer | Redis | TTL 기반 사용자 중복 방지, 전체 표시 카운트, DB 반영 대기 카운트 저장 |
| Message Queue | RabbitMQ | 버퍼 비동기 delta 이벤트 전달 |
| Load Test | k6 | API 성능 측정 |
| Runtime | Docker Compose | App, MariaDB, Redis, RabbitMQ 전체 실행 |

## 3. 구현 방식

### 3.1 동기 처리

동기 방식은 요청이 들어오면 DB에서 `like_count = like_count + 1` 원자적 증가 쿼리를 실행한다. 비관적 락은 사용하지 않지만, 하나의 SQL update로 증가하므로 동시에 요청이 들어와도 lost update가 발생하지 않는다.

```text
Client
  -> /api/v1/like/sync
  -> Spring Boot
  -> MariaDB UPDATE like_count = like_count + 1
  -> HTTP 200 응답
```

장점은 구조가 단순하고 최종 좋아요 수를 원자적으로 증가시킨다는 점이다. 단점은 모든 요청이 DB write와 row lock 경합을 직접 통과해야 한다는 점이다.

### 3.2 버퍼 비동기 처리

버퍼 비동기 방식은 사용자가 좋아요를 누르면 Redis에 먼저 누적한다. 요청 시점에는 DB를 업데이트하지 않고, RabbitMQ에도 직접 메시지를 보내지 않는다. Redis Lua 스크립트가 TTL 기반 사용자 중복 기록과 두 카운트 증가를 원자적으로 처리한다. display count는 처음 사용할 때 DB `like_count`를 기준값으로 초기화하므로 사용자에게 전체 좋아요 수를 보여줄 수 있다. Scheduler는 pending count를 Redis outbox로 옮긴 뒤 RabbitMQ broker confirm을 받은 이벤트만 outbox에서 제거하고, Consumer가 DB에 `like_count += delta`로 반영한다.

```text
Client
  -> /api/v1/like/buffered-async
  -> Redis 영상 존재 여부 확인
  -> Redis 사용자별 TTL 키 중복 체크
  -> Redis 전체 display count + 1
  -> Redis pending count + 1
  -> HTTP 200 응답

Scheduler
  -> Redis pending count를 outbox event로 원자적 이동
  -> RabbitMQ LikeCountDeltaEvent(eventId, videoId, delta) 발행
  -> broker confirm 성공 시 outbox event 삭제

Consumer
  -> LikeCountDeltaEvent 소비
  -> eventId 중복 여부 확인
  -> MariaDB like_count += delta 및 eventId 저장
  -> DB 커밋 성공 후 자동 ACK
```

이 방식은 좋아요 10,000건이 짧은 시간에 발생해도 DB update를 10,000번 실행하지 않고, 일정 시간 동안 모인 delta를 기준으로 적은 횟수의 update로 반영할 수 있다.

사용자별 중복 키는 기본 30일 TTL을 적용한다. 이 키는 `volatile-lru` 정책의 eviction 대상이므로 메모리 압박 시 중요 카운트나 outbox가 아니라 오래된 중복 방지 키부터 정리된다. TTL이 지난 사용자는 다시 좋아요 요청을 보낼 수 있으므로, 영구 중복 방지가 요구되는 서비스라면 별도의 사용자-좋아요 영속 저장소가 필요하다.

## 4. 테스트 환경과 조건

| 항목 | 값 |
| --- | --- |
| 테스트 도구 | k6 |
| 대상 서버 | `http://localhost:8080` |
| 테스트 대상 영상 | `videoId = 1` |
| 최대 VU | 500 |
| Ramp-up | 1분 동안 500 VU까지 증가 |
| 유지 구간 | 3분 동안 500 VU 유지 |
| Ramp-down | 1분 동안 0 VU로 감소 |
| 총 실행 시간 | 5분 |
| 성공 조건 | HTTP 200 응답 |

사용한 실행 명령:

```powershell
k6 run load-test-sync.js
k6 run load-test-buffered-async.js
```

## 5. 테스트 결과

### 5.1 동기 처리 결과

| 지표 | 결과 |
| --- | ---: |
| 전체 요청 수 | 140,833 |
| 처리량 | 469.44 req/s |
| 성공률 | 100.00% |
| 실패율 | 0.00% |
| 평균 응답 시간 | 752.69ms |
| 중앙값 응답 시간 | 852.30ms |
| 최대 응답 시간 | 1.52s |
| p90 응답 시간 | 1.02s |
| p95 응답 시간 | 1.05s |

동기 방식은 모든 HTTP 요청을 실패 없이 처리했다. 다만 평균 응답 시간이 약 753ms이고 p95가 1.05초까지 증가했다. DB를 요청 경로에서 직접 갱신하기 때문에 요청이 많아질수록 응답 시간이 길어진다.

### 5.2 버퍼 비동기 처리 결과

| 지표 | 결과 |
| --- | ---: |
| 전체 요청 수 | 1,103,136 |
| 처리량 | 3,675.83 req/s |
| 성공률 | 100.00% |
| 실패율 | 0.00% |
| 평균 응답 시간 | 8.31ms |
| 중앙값 응답 시간 | 7.88ms |
| 최대 응답 시간 | 166.98ms |
| p90 응답 시간 | 13.05ms |
| p95 응답 시간 | 14.76ms |

버퍼 비동기 방식은 동기 방식보다 훨씬 높은 처리량과 낮은 응답 시간을 보였다. HTTP 요청 시점에서 Redis만 갱신하고, RabbitMQ 발행과 DB 반영은 Scheduler/Consumer 경로로 분리했기 때문이다.

## 6. 비교 분석

| 지표 | 동기 처리 | 버퍼 비동기 처리 | 비교 |
| --- | ---: | ---: | ---: |
| 전체 요청 수 | 140,833 | 1,103,136 | 버퍼 비동기가 약 7.83배 |
| 처리량 | 469.44 req/s | 3,675.83 req/s | 버퍼 비동기가 약 7.83배 |
| 평균 응답 시간 | 752.69ms | 8.31ms | 약 98.90% 감소 |
| p95 응답 시간 | 1.05s | 14.76ms | 약 98.59% 감소 |
| 실패율 | 0.00% | 0.00% | 동일 |
| 성공률 | 100.00% | 100.00% | 동일 |

### 6.1 DB 부하 관점

| 방식 | 요청 10,000건 발생 시 예상 DB update |
| --- | --- |
| 동기 처리 | 10,000번 |
| 버퍼 비동기 처리 | flush 주기와 videoId 수에 따라 소수 번 |

동기 방식은 요청 수만큼 DB update가 발생한다. 반면 버퍼 비동기 방식은 Redis에 좋아요 수를 모아두고 delta 단위로 DB에 반영하므로, 대규모 트래픽에서 DB update 횟수를 크게 줄일 수 있다.

### 6.2 정합성 관점

동기 방식은 비관적 락 없이도 DB의 원자적 증가 쿼리를 사용하므로 lost update를 방지한다. 다만 모든 성공 요청이 DB write를 수행하므로 높은 동시성에서는 응답 시간이 증가할 수 있다.

버퍼 비동기 방식은 최종적 일관성을 전제로 한다. 요청 직후 DB 값은 아직 반영되지 않았을 수 있지만, Scheduler와 Consumer가 처리하면 DB 값이 Redis pending delta만큼 수렴한다. RabbitMQ 발행 실패 시 outbox를 남기고 재시도하며, 재전달된 eventId는 DB에 한 번만 반영한다.

## 7. 결론

| 관점 | 더 적합한 방식 | 이유 |
| --- | --- | --- |
| 구현 단순성 | 동기 처리 | Redis, RabbitMQ, Scheduler 운영 불필요 |
| 요청 응답 속도 | 버퍼 비동기 처리 | 요청 경로에서 Redis만 갱신 |
| 대규모 요청 수용 | 버퍼 비동기 처리 | 처리량이 훨씬 높음 |
| DB update 횟수 감소 | 버퍼 비동기 처리 | Redis 누적값을 delta 단위로 일괄 반영 |
| 최종적 일관성 학습 | 버퍼 비동기 처리 | 시간이 지나며 DB 값이 수렴 |

프로젝트의 목적은 “좋아요 요청이 발생할 때마다 DB에 직접 update를 실행하지 않고, Redis와 메시지 큐를 활용해 DB 부하를 낮추는 구조를 이해하는 것”이다. 이 목적에는 버퍼 비동기 방식이 더 적합하다.

## 8. 추가 확인이 필요한 항목

버퍼 비동기 방식은 HTTP 응답 성능만으로 최종 성공 여부를 판단하면 안 된다. 다음 항목을 함께 확인해야 한다.

| 확인 항목 | 확인 방법 | 의미 |
| --- | --- | --- |
| RabbitMQ `like.aggregate.queue` | RabbitMQ 관리 화면 | 버퍼 비동기 delta 이벤트 대기 수 |
| Redis pending count | Redis CLI | 아직 RabbitMQ로 flush되지 않은 좋아요 수 |
| DB 최종 반영 수 | MariaDB에서 `video.like_count` 조회 | 최종 좋아요 수 반영 여부 |

DB 최종 값 확인 명령:

```powershell
docker exec -it mariadb-container mariadb -u db_user -pdb_password like_system -e "select id, like_count from video where id = 1;"
```

## 9. 학습 포인트

- DB 직접 update 방식은 단순하지만 요청 수가 많아지면 DB 부하가 커진다.
- sync 방식은 원자적 증가 쿼리로 lost update를 막지만, 요청 수만큼 DB write가 발생한다.
- Redis display count는 DB 기준값에서 시작하는 전체 표시용 캐시이므로 사용자에게 즉각적인 좋아요 수 증가를 보여줄 수 있다.
- Redis 사용자 중복 키는 TTL을 적용해 메모리 사용량을 시간 범위로 제한하고, `volatile-lru`가 실제로 동작할 수 있게 한다.
- Redis Lua script는 중복 기록과 카운트 증가가 부분 성공으로 어긋나는 문제를 막는다.
- Redis outbox와 RabbitMQ broker confirm은 발행 실패 시 delta 유실을 막는다.
- RabbitMQ Consumer는 DB 커밋 뒤 자동 ACK하고 eventId를 저장해 재전달을 중복 반영하지 않는다.
- 대규모 트래픽에서 중요한 것은 단순히 비동기화하는 것이 아니라 DB update 횟수를 줄이는 것이다.
- 버퍼 비동기 구조는 최종적 일관성을 전제로 한다. 사용자는 빠른 응답을 받고 DB 값은 잠시 뒤 정확한 값으로 수렴한다.
