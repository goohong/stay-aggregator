---
topic: 회복탄력성 기능을 Reactor 로 할지 resilience4j 로 할지
checked: 2026-09-17
---

# resilience4j 와 Reactor 연산자 비교

재시도·타임아웃·동시 실행 수 제한을 Reactor 연산자로 했는데, 이것이 이미 있는 라이브러리를 두고 다시 만든 것인지 확인했다.
서킷 브레이커를 넣을 때 어느 모듈을 쓸지도 함께 봤다. 여기에는 확인한 사실만 적고 판단은 해당 ADR 에 적는다.

실험은 저장소 밖(`/tmp/r4j-check`)에서 Kotlin 2.4.20, JVM toolchain 25, reactor-core 3.8.7 로 했다.

## 요약

- resilience4j 의 재시도와 타임아웃 연산자는 **안에서 Reactor 의 같은 연산자를 부른다.** 바꿔도 동작은 같고 한 겹이 늘어난다
- 서킷 브레이커는 Reactor 에 없다
- resilience4j 의 설정 파일·지표 엔드포인트 연동은 Spring Boot 모듈의 기능인데 **Boot 4 모듈은 릴리스되지 않았다**
- `resilience4j-reactor:2.3.0` 은 reactor-core 3.8.7 에서 컴파일되고 서킷 열림까지 실행으로 확인됐다

## 1. 안에서 무엇을 부르는가 (javap 로 확인)

| resilience4j 클래스 | 안에서 부르는 Reactor 연산자 |
|---|---|
| `reactor.retry.RetryOperator` | `reactor.util.retry.Retry.withThrowable`, `Mono.retryWhen`, `Flux.retryWhen` |
| `reactor.timelimiter.TimeLimiterOperator` | `Mono.timeout(Duration)`, `doOnSuccess`, `doOnError` |

확인 명령: `javap -c -p -classpath resilience4j-reactor-2.3.0.jar io.github.resilience4j.reactor.retry.RetryOperator` (2026-09-17)

## 2. 서킷 브레이커 연산자가 하는 일 (javap 로 확인)

`reactor.circuitbreaker.operator.MonoCircuitBreaker` 와 `CircuitBreakerSubscriber` 가 서킷에 알리는 시점이다.

| 시점 | 부르는 것 |
|---|---|
| 구독할 때 | `tryAcquirePermission` |
| 값이 왔을 때 | `onResult` (걸린 시간과 함께) |
| 끝났을 때 | `onSuccess` (걸린 시간과 함께) |
| 오류가 났을 때 | `onError` (걸린 시간과 함께) |
| **취소됐을 때** | `onSuccess` 또는 `releasePermission` |

두 번 알리지 않도록 `AtomicBoolean` 두 개(`successSignaled`, `eventWasEmitted`)를 필드로 든다.

**취소 처리가 우리 코드와 걸린다.** 검색 시간 한계(`.timeout(search.budget)`)를 넘기면 진행 중인 호출을 취소한다.
코어 모듈만 쓰면 취소 때 허가를 돌려주는 일을 직접 짜야 한다. 돌려주지 않았을 때 어떻게 되는지는 **실행해서 확인하지 않았다.**

## 3. 호환성 (실행으로 확인)

`resilience4j-circuitbreaker:2.3.0` + `resilience4j-reactor:2.3.0` + `reactor-core:3.8.7`. 창 크기 4, 실패율 50%.

```
1~3번째 호출 → IllegalStateException, 상태 CLOSED
4번째 호출 뒤 → changed state from CLOSED to OPEN
5·6번째 호출 → CallNotPermittedException: CircuitBreaker 'supplierA' is OPEN and does not permit further calls
```

- Gradle 이 `reactor-core:3.4.24 -> 3.8.7` 로 올려 해석했고 연결 오류는 없었다
- `resilience4j-reactor` POM 이 retry·timelimiter·bulkhead·ratelimiter·micrometer 를 runtime 으로 함께 끈다
- 실험 프로젝트에서는 `slf4j-api:1.7.30` 이 해석됐다. 우리 저장소는 Boot 가 `slf4j-api:2.0.18` 로 관리한다. 넣은 뒤 어느 쪽으로 풀리는지는 저장소에서 확인한다

## 4. resilience4j 에만 있는 것

| 기능 | 출처 |
|---|---|
| 재시도·동시 실행 수 제한·타임아웃의 지표(micrometer) | [Micrometer](https://resilience4j.readme.io/docs/micrometer) 절 "Retry Metrics", "Bulkhead Metrics", "TimeLimiter" |
| 설정 파일로 정책 지정, actuator 의 지표·상태·이벤트 엔드포인트 | [Getting Started](https://resilience4j.readme.io/docs/getting-started-3) 절 "Configuration", "Metrics endpoint", "Health endpoint", "Events endpoint". Spring Boot 모듈의 기능이다 |
| 여러 요청이 함께 쓰는 동시 실행 수 한도 | [Bulkhead](https://resilience4j.readme.io/docs/bulkhead) 절 "Create and configure a Bulkhead". 인스턴스를 공유하면 요청들 사이에서 한도가 걸린다 |

위 문서 인용은 비교 에이전트가 문서를 열어 적은 것이고, 이 기록을 쓰면서 원문을 다시 대조하지는 않았다.

## 5. Reactor 쪽이 다른 점

- **넘친 호출을 기다리게 한다.** `flatMap(mapper, n)` 은 n 개를 넘는 묶음을 기다렸다 부른다. resilience4j 의 Reactor bulkhead 연산자는 `tryAcquirePermission` 만 부르고 실패하면 `BulkheadFullException` 을 낸다(javap). 그대로 바꾸면 묶음이 실패로 바뀐다
- **다 써도 실패하면 원래 실패를 올린다.** 지금 코드는 `onRetryExhaustedThrow { signal.failure() }` 로 원래 예외를 올린다 ([ADR-0027](../adr/0027-spec-violation-handling-criteria.md) 의 "한 가지 실패"). resilience4j 가 어떻게 올리는지는 확인 못 함

## 확인하지 못한 것

- RetryOperator·TimeLimiterOperator·BulkheadOperator 를 실행하지는 않았다
- 코어만 쓸 때 취소에서 허가를 돌려주지 않으면 실제로 무엇이 일어나는지
- resilience4j 재시도가 횟수를 다 썼을 때 원래 예외를 올리는지 감싸는지
