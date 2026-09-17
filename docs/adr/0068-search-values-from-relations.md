---
id: 0068
title: 검색 연동 값을 관계식과 근거로 정하기로 결정
status: accepted
date: 2026-09-17
---

# 0068. 검색 연동 값을 관계식과 근거로 정하기로 결정

## Context

검색 연동의 값(타임아웃 셋, 재시도, 서킷, 격리 보관 기간, 캐시 TTL)을 설정에 두고 "임시값"으로 남겨 왔다
([ADR-0045](0045-search-concurrency-and-budget.md), [ADR-0051](0051-retry-transient-supplier-failures.md), [ADR-0055](0055-quarantine-grouped-in-db.md),
[ADR-0056](0056-circuit-breaker-per-supplier-outside-retry.md), [ADR-0065](0065-availability-cache-redis.md), [ADR-0066](0066-connect-timeout-in-shared-webclient.md), Q33).

[검색 지연 측정](../research/search-latency.md) 은 값이 아니라 관계식을 보여 줬다. 실제 공급사의 지연 분포는 모른다.
그래서 값마다 정답이 있는 것이 아니다. 이 ADR 은 **값끼리 지켜야 하는 관계식**을 먼저 세우고, 관계식이 남긴 자리를 외부 기본값이나 판단으로 채운다.
각 값이 인용인지, 계산인지, 판단인지를 구분해 적는다.

확인한 사실 (2026-09-17 에 원문을 열어 대조했다)

| 사실 | 원문 |
|---|---|
| 사용자의 주의가 대화에 머무는 한계는 10초 정도다 | [Nielsen, Response Times: The 3 Important Limits](https://www.nngroup.com/articles/response-times-3-important-limits/): "10 seconds is about the limit for keeping the user's attention focused on the dialogue." |
| AWS SDK 의 연결 타임아웃 기본값은 standard 3100ms, in-region 1100ms 다. in-region 은 같은 리전 안에서 호출하는 앱용이다 | [AWS SDKs and Tools, Smart configuration defaults](https://docs.aws.amazon.com/sdkref/latest/guide/feature-smart-config-defaults.html) 의 표 `connectTimeoutInMillis` 행: "3100 \| 1100 \| 3100 \| 30000" / "in-region – Builds on the standard mode and includes optimization tailored for applications that call AWS services from within the same AWS Region." |
| AWS SDK 는 요청 한도 초과(throttling)를 다른 일시적 실패보다 긴 대기에서 시작해 재시도한다 | [AWS SDKs and Tools, Retry behavior](https://docs.aws.amazon.com/sdkref/latest/guide/feature-retry-behavior.html) 표 "Base delays by error type": "Transient errors are retried with a short base delay (50 ms)" / "Throttling errors are retried with a longer base delay (1,000 ms)" / "A longer base delay gives time to recover capacity." (이 페이지는 2026년 개정본이다) |
| resilience4j 는 반열림(HALF_OPEN) 상태에서 허용한 호출 수만 통과시키고, 그 호출이 다 끝날 때까지 나머지를 거절한다 | [resilience4j CircuitBreaker](https://resilience4j.readme.io/docs/circuitbreaker): "Further calls are rejected with a CallNotPermittedException, until all permitted calls have completed." |
| resilience4j 기본값은 최소 호출 수 100, 윈도 100, 반열림 허용 10, 열림 유지 60000ms 이고, 문서는 그 값의 이유를 적지 않는다 | 같은 문서의 설정 표 |
| Envoy 는 호스트를 30초 차단하고, 차단될 때마다 그 배수로 늘려 최대 300초까지 둔다 | [Envoy OutlierDetection](https://www.envoyproxy.io/docs/envoy/latest/api-v3/config/cluster/v3/outlier_detection.proto) `base_ejection_time`: "The real time is equal to the base time multiplied by the number of times the host has been ejected and is capped by max_ejection_time. Defaults to 30000ms or 30s." / `max_ejection_time`: "the default value (300000ms or 300s)" |
| Istio 도 기본 차단 시간이 30초다 | [istio/api destination_rule.proto](https://raw.githubusercontent.com/istio/api/master/networking/v1alpha3/destination_rule.proto) `base_ejection_time`: "Default is 30s." (istio.io 문서 페이지는 이 proto 에서 만들어지나 페이지 자체는 열지 못했다) |
| Microsoft 는 처음에 몇 초 열어 두고, 풀리지 않으면 몇 분으로 늘리라고 한다 | [Circuit Breaker pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker) 절 "Solution": "You can place the circuit breaker in the Open state for a few seconds initially. If the failure isn't resolved, increase the time-out to a few minutes and adjust accordingly." |
| 숙박 API 의 요청 한도 차단 시간 예: Booking.com Demand API 는 보통 1분, Expedia Rapid 는 최소 5분 기다리라고 한다 | [Booking.com Demand API, Rate limiting](https://developers.booking.com/demand/docs/development-guide/rate-limiting): "Access is then temporarily restricted for a brief period (typically 1 minute)" / [Expedia Rapid, Error responses](https://developers.expediagroup.com/rapid/resources/error-responses-lodging?locale=en_US) 절 "429 - Rate limit error": "please wait for at least 5 minutes before trying a corrected request." |
| Reactor 의 재시도 대기는 `min(최소 백오프 × 2^i, 최대 백오프)` 에 무작위를 더하고, 더하는 값은 그 대기의 jitter 배(기본 0.5)와 `최대 백오프 − 대기` 중 작은 것 미만이다 | reactor-core 3.8.7 소스 `RetryBackoffSpec.generateCompanion` 의 `highBound = Math.min(maxBackoff.minus(nextBackoff).toMillis(), jitterOffset)`. [javadoc](https://projectreactor.io/docs/core/release/api/reactor/util/retry/RetryBackoffSpec.html) `jitter`: "Defaults to 0.5" |
| 캐시 스탬피드 방지 XFetch 는 `Time() − Δβ log(rand()) ≥ expiry` 이면 미리 다시 계산한다. Δ 는 다시 계산하는 데 걸린 시간, β 기본값은 1 이다 | [Vattani, Chierichetti, Lowenstein, VLDB 2015](https://www.vldb.org/pvldb/vol8/p886-vattani.pdf) Figure 3 과 그 설명: "The parameter β defaults to 1" |

## Options

값을 정하는 방식
- **(가) 관계식을 먼저 세우고, 관계식이 남긴 자리를 외부 기본값이나 판단으로 채운다** ← 채택 — 값이 바뀌어도 무엇을 함께 바꿔야 하는지가 남는다. 대신 판단값이 여전히 있고, 그 사실을 적어야 한다
- **(나) 외부 라이브러리 기본값을 그대로 쓴다** — 근거를 따로 만들 필요가 없다. 대신 기본값끼리 우리 구조에서 서로 맞지 않는다(resilience4j 최소 호출 100 은 chunk 수가 적은 공급사에서 서킷이 열리지 않는다)
- **(다) 실제 공급사 지연을 잴 때까지 임시값으로 둔다** — 대신 잴 수 있는 공급사가 없어 끝나지 않는다

관계식을 어디서 검사하는가
- **(ㄱ) 깨지면 장치가 조용히 동작하지 않는 관계만 설정 객체가 검사한다** ← 채택 — 기존 "호출 타임아웃 < 검색 전체 타임아웃" 검사와 같은 기준이다. 그 관계가 깨지면 검색 전체 타임아웃이 chunk 를 먼저 취소하고 서킷은 취소를 세지 않아(ADR-0056) 서킷이 열리지 않는데, 테스트도 기동도 통과한다
- **(ㄴ) 모든 관계를 검사한다** — 대신 판단값(여유 1.55초, 최소 호출 = 동시 호출 × 2)까지 기동 조건이 되어, 판단을 바꾸려면 코드도 바꿔야 한다
- **(ㄷ) 검사하지 않고 문서로만 둔다** — 대신 설정만 바꾼 배포가 서킷을 조용히 끈다

재시도 사이에 실패 종류가 바뀔 때(5xx 뒤 429 등)
- **(A) 지금까지 만난 실패 종류 중 가장 적은 재시도 횟수를 그 chunk 의 한도로 한다** ← 채택 — 최악의 시간이 종류별 최악 중 큰 값을 넘지 않아 아래 관계식이 그대로 성립한다. 429 는 속도를 늦추라는 요청이라 그 뒤에 재시도를 더 쌓지 않는 것과도 맞다. 대신 503 뒤 429 가 오면 5xx 한도(2회)보다 적게 재시도한다
- **(B) 종류마다 따로 센다** — 대신 503·503·429·503 처럼 섞이면 네 번 호출해 최악 9.95초로 검색 전체 타임아웃을 넘는다
- **(C) 종류와 상관없이 전체 횟수를 세고 지금 실패 종류의 한도와 비교한다** — 대신 429 뒤 503 이 두 번 오면 최악 7.8초로 여유를 다 쓴다

## Decision

**값**

| 값 | 이전 | 정한 값 | 종류 | 근거 |
|---|---|---|---|---|
| 검색 전체 타임아웃 `stay.search.timeout` | 8s | **8s 유지** | 인용 − 판단 | Nielsen 의 10초에서 네트워크 왕복과 화면 그리기 몫 2초를 뺐다. 2초는 판단값이다 |
| 재고·요금 호출 타임아웃 `availability-timeout` | 5s | **2s** | 계산 + 판단 | 관계식 ②. 3 × 2s + 0.45s = 6.45s 이고 남은 1.55s 는 매핑 읽기 같은 앞 단계 몫이다. 여유 크기는 판단값이다 |
| 연결 타임아웃 `connect-timeout` | 3.1s | **1.1s** | 인용 | AWS SDK in-region 모드 기본값. 관계식 ① 로 호출 타임아웃 2s 보다 짧아야 하는데 standard 값 3.1s 는 그보다 길다 |
| 5xx·연결 실패 재시도 `retry` | 2회, 100ms, 1s | **유지** | 인용 + 판단 | 횟수는 Google SRE (ADR-0051). 최소 백오프 100ms 는 AWS 의 50ms 와 다르다. 관계식 ② 안에 들어가 바꿀 이유가 없어 유지했다(판단) |
| 429·`E429` 재시도 `throttled-retry` | 5xx 와 같음 | **1회, 최소 백오프 1s, 최대 백오프 1.5s** | 인용 + 계산 | 대기는 AWS SDK throttling 기준 대기 1,000ms. 횟수는 관계식 ③. 최대 백오프 1.5s 는 무작위(0.5)가 잘리지 않는 값이다 |
| 서킷 실패율 임계값 | 50% | **50% 유지** | 인용 | resilience4j 기본값 |
| 서킷 슬라이딩 윈도 | 20 | **20 유지** | 판단 | 이전 값을 유지했다. 최소 호출 수(8)보다 커야 한다는 것 말고는 따로 근거를 세우지 않았다 |
| 서킷 최소 호출 수 | 10 | **8** | 계산 + 판단 | 관계식 ⑤. 동시 호출 수 × 2. 응답 없는 공급사면 검색 두 건(각 chunk 4개)에 열린다. "두 건"은 판단값이다 |
| 서킷 열림 유지 시간 | 30s | **30s 고정** | 인용 | Envoy·Istio 기본 차단 시간 |
| 반열림 상태에서 허용하는 호출 수 | 3 | **4** | 계산 | 관계식 ④. 동시 호출 수와 같다 |
| 공급사당 동시 호출 수 | 4 | **4 유지** | 판단 | ADR-0045. 서버 규모에 따라 달라지는 값이다 |
| 격리 보관 기간 | 30일 | **30일 유지** | 판단 | 운영자가 한 달에 한 번은 격리 기록을 본다는 가정 |
| 캐시 TTL | 없음 | **60s** (캐시 구현 때 적용) | 계산 + 판단 | 관계식 ⑥ 의 하한 9.2s 이상. 60s 는 판단값이다. 이것이 캐시와 원본의 정합성 오차 허용 범위다(최대 60초 전 재고가 나간다) |

**관계식**

| # | 관계 | 지금 값으로 | 설정 객체가 검사하는가 |
|---|---|---|---|
| ① | 연결 타임아웃 < 호출 타임아웃 | 1.1s < 2s | 예 (ADR-0066). 깨지면 연결 타임아웃이 발동할 일이 없다 |
| ② | 호출 타임아웃 × (재시도 + 1) + 대기 최대치 합 + 앞 단계 여유 ≤ 검색 전체 타임아웃 | 2 × 3 + (0.15 + 0.3) + 1.55 = 8s | **여유를 뺀 부분만** 예. `RetryPolicy.worstCase < search.timeout`. 여유는 판단값이라 검사하지 않는다 |
| ③ | 429 사슬: 호출 타임아웃 × (재시도 + 1) + 대기 최대치 ≤ 검색 전체 타임아웃 | 2 × 2 + 1.5 = 5.5s. 재시도 2회면 2 × 3 + 1.5 + 1.5 = 9s 로 넘는다 | 예. ② 와 같은 검사를 429 기준에도 한다 |
| ④ | 반열림 상태에서 허용하는 호출 수 ≥ 공급사당 동시 호출 수 | 4 ≥ 4 | 아니요. 깨져도 서킷은 동작하고, 반열림 상태의 검색에서 첫 chunk 일부가 거절될 뿐이다 |
| ⑤ | 최소 호출 수 = 동시 호출 수 × 2 | 8 = 4 × 2 | 아니요. 판단값이다 |
| ⑥ | 캐시 TTL ≥ 4.6 · β · Δ | Δ = 호출 타임아웃 2s, β = 1 이면 9.2s | 캐시 구현 때 정한다 |

- ② 의 대기 최대치: 재시도 i 번째(0 부터) 대기는 `min(최대 백오프, 1.5 × 최소 백오프 × 2^i)` 를 넘지 않는다(위 Reactor 소스). 100ms·1s 면 150ms + 300ms = 0.45s
- ③ 의 대기 최대치: 1s × 1.5 = 1.5s. 최대 백오프가 1s 면 무작위가 잘려 매번 정확히 1s 를 기다린다
- ⑥ 의 4.6: XFetch 가 미리 앞당기는 시간은 평균 βΔ 인 지수분포다. 앞당김이 TTL 보다 클 확률은 `e^(−TTL/βΔ)` 이고, TTL = 4.6βΔ 이면 약 1%(ln 100 ≈ 4.6)다. TTL 이 이보다 짧으면 방금 채운 값도 곧바로 다시 계산될 확률이 커진다. 논문의 문장이 아니라 논문의 식에서 계산한 값이다
- 관계식은 **chunk 한 라운드**(동시 호출 수 × 50 = 숙소 200개)까지를 담는다. 숙소가 더 많으면 라운드가 늘어 ([검색 지연 측정](../research/search-latency.md)) 뒤 라운드의 chunk 는 검색 전체 타임아웃으로 취소될 수 있고 서킷은 그것을 세지 않는다

**429 재시도**
- 공급사 실패 예외에 "요청 한도 초과인가"(`throttled`)를 더한다. 분류는 `transient` 와 같은 곳에서 한다. HTTP 429 는 `SupplierFailure.kt`, B 의 `E429` 는 어댑터다 (ADR-0051 의 표). 429 는 여전히 재시도 가능한 실패다
- 검색은 요청 한도 초과면 `throttled-retry` 기준으로, 그 밖의 재시도 가능한 실패는 `retry` 기준으로 기다린다
- 한 chunk 안에서 실패 종류가 바뀌면 지금까지 만난 종류 중 가장 적은 재시도 횟수를 한도로 한다 (A)
- 429 로 열린 서킷도 5xx 로 열린 서킷과 구분하지 않는다

**이유**

- 수치에는 정답이 없다. 남길 수 있는 것은 어떤 관계 때문에 그 값이 되었고, 어느 부분이 판단인지다. 그래야 값 하나를 바꿀 때 함께 봐야 하는 값이 보인다
- 호출 타임아웃은 검색 전체 타임아웃에서 거꾸로 계산했다. 재시도까지 다 쓴 chunk 가 검색 전체 타임아웃 안에 끝나야 서킷이 그 공급사를 실패로 센다
- 반열림 허용 수가 동시 호출 수보다 적으면 시험 검색의 첫 chunk 들 중 일부가 허용된 호출이 끝날 때까지 거절된다(위 resilience4j 문장). 시험 검색 하나가 공급사 회복을 판단하는 데 필요한 만큼을 허용한다
- 열림 유지 시간은 손실의 종류가 다르다. 짧으면 장애 상태인 공급사에 시험 검색이 자주 가서 그 검색이 최대 약 8초 느려진다. 길면 공급사가 회복한 뒤에도 그 공급사 결과를 잃는 검색이 는다. 어느 쪽이 더 나쁜지 정할 근거가 없어 널리 쓰는 프록시의 기본값을 따랐다(판단)

## Consequences

**얻는 것**
- 값마다 인용·계산·판단이 구분되어, 값을 바꿀 때 무엇을 다시 봐야 하는지 남는다
- 무응답 공급사의 chunk 가 2초에 실패로 끝나 검색 전체 타임아웃보다 먼저 서킷에 세어진다
- 설정만 바꾼 배포가 관계식 ②·③ 을 깨면 앱이 기동에 실패한다
- 요청 한도 초과에 곧바로 재시도를 쌓지 않는다

**잃는 것**
- **호출 타임아웃 2초는 실제 공급사를 잰 값이 아니다.** 정상 응답이 2초를 넘는 공급사는 늘 타임아웃이 된다
- **연결 타임아웃 1.1초는 같은 리전 기준값이다.** 공급사가 멀리 있어 연결에 오래 걸리면 정상 공급사도 실패한다
- 재시도 대기 계산이 Reactor 의 무작위 방식에 기대고 있다. Reactor 가 그 방식을 바꾸면 `RetryPolicy.worstCase` 가 틀린 말을 한다
- 여유 1.55초와 최소 호출 수는 검사하지 않아 판단을 벗어난 값도 기동한다
- 관계식이 숙소 200개(한 라운드)까지만 담는다
- 503 뒤에 429 가 오면 5xx 기준보다 적게 재시도한다
- 캐시 TTL 60초 동안 실제와 다른 재고가 나갈 수 있다 (ADR-0065)

**다시 볼 것**
- **열림 유지 시간을 늘려 가는 방식.** 30초에서 시작해 차단될 때마다 늘려 5분까지(Envoy `max_ejection_time` 300초, Microsoft 의 "몇 초에서 몇 분으로"). resilience4j 는 `waitIntervalFunctionInOpenState` 로 할 수 있다
- 429 에 `Retry-After` 를 주는 공급사가 오면 그 값을 따른다. 그때 429 로 열린 서킷을 따로 볼지 본다(Booking.com 1분, Expedia 5분은 참고로만 둔다)
- 실제 공급사의 지연 분포를 알게 되면 호출 타임아웃과 연결 타임아웃
- 서버 규모에 따른 동시 호출 수와 풀 크기

## Discussion

- **사용자 판단** — "수치엔 정답이 없고, 근거와 판단 과정을 드러내는 게 중요하다." 값 표의 값과 근거는 사용자가 정했다
- **계산 실수** — 재고·요금 호출 타임아웃을 처음에 2.5초로 정했다. 3 × 2.5 + 0.45 ≈ 7.95초가 8초에 닿아, 매핑 읽기 같은 앞 단계 시간이 더해지면 검색 전체 타임아웃이 chunk 를 먼저 취소하고 서킷이 그 공급사를 세지 못한다. 조사 중에 발견해 2초로 고쳤다(6.45초, 여유는 판단값)
- **AI 가 정한 것** — 관계식을 설정 객체에서 어디까지 검사할지는 기존 검사와 같은 기준((ㄱ))으로 AI 가 정했다. 재시도 중 실패 종류가 바뀌는 경우(A)는 사용자 표에 없었고, 세 방식의 최악 시간을 계산해 관계식이 유지되는 쪽으로 AI 가 정했다
- **인용 대조에서 드러난 것** — 캐시 TTL 하한 "4.6·β·Δ" 는 논문에 적힌 식이 아니었다. 논문의 XFetch 식에서 1% 기준으로 계산한 값이라 표에 "계산"으로 적었다. AWS 재시도 문서는 2026년 개정본이라 다른 일시적 실패의 기준 대기가 50ms 로 적혀 있다. 우리 100ms 와 다르다는 것을 표에 남겼다

## Verification

설정 객체가 재시도까지 다 쓴 chunk 의 최악 시간을 검색 전체 타임아웃과 비교하는지
  → `StayPropertiesTest.재시도까지 다 쓴 시간이 검색 전체 타임아웃을 넘으면 만들 수 없다`
  → `StayPropertiesTest.호출 타임아웃이 검색 전체 타임아웃보다 길면 만들 수 없다`

대기 최대치 계산이 Reactor 의 방식과 맞는지 (② 의 0.45초)
  → `StayPropertiesTest.재시도까지 다 쓴 시간의 상한은 Reactor 가 대기에 무작위를 섞는 방식대로 계산한다`

운영 설정 파일의 값이 이 표의 값이고 관계식을 지키는지
  → `StayPropertiesTest.운영 설정 파일의 값이 ADR-0068 에서 정한 값이고 설정 객체의 검사를 통과한다`

설정한 서킷 기준이 서킷에 그대로 들어가는지
  → `SupplierCircuitBreakersTest.설정한 서킷 기준이 그대로 공급사 서킷에 들어간다`
