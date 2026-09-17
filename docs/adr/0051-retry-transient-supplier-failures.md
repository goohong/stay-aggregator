---
id: 0051
title: 일시적인 공급사 실패만 재시도하고 재시도 여부는 consumer 가 정하기로 결정
status: accepted
date: 2026-09-17
---

# 0051. 일시적인 공급사 실패만 재시도하고 재시도 여부는 consumer 가 정하기로 결정

## Context

[ADR-0050](0050-partial-chunk-failure.md) 이 잃는 것에 이렇게 적었다. 묶음 하나가 실패하면 **그 묶음의 숙소는 이 검색에 없다**.
숙소가 3,000개면 묶음 하나가 50개다. 503 이 한 번 온 것 때문에 50개가 빠진다.

재시도는 요구사항의 선택 항목이고, [ADR-0045](0045-search-concurrency-and-budget.md) 가
"한도에 걸렸을 때의 처리는 재시도·서킷과 같은 자리에서 본다"로 미뤄 두었다. 그 자리다.

지금은 모든 공급사 실패가 [SupplierResponseException](../../app/src/main/kotlin/com/stayaggregator/supplier/SupplierResponseException.kt) 하나로 온다.
안에 "다시 불러도 되는가"가 없어서, 재시도하려면 그것부터 가려야 한다.

확인한 사실

| 사실 | 원문 |
|---|---|
| 시도 세 번이면 그만둔다. 세 번 실패했으면 더 해도 소용없을 가능성이 높다 | [Google SRE, Handling Overload](https://sre.google/sre-book/handling-overload/) 절 "Deciding to Retry": "If a request has already failed three times, we let the failure bubble up to the caller." / "if a request has already landed on overloaded tasks three times, it's relatively unlikely that attempting it again will help because the whole datacenter is likely overloaded." |
| 재시도 비율이 10% 를 넘으면 더 하지 않는다 | 같은 절: "A request will only be retried as long as this ratio is below 10%." |
| 백오프만으로는 부족하고 무작위를 섞어야 한다 | [AWS Architecture Blog, Exponential Backoff And Jitter](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/) (Marc Brooker, 2015-03-04): "The solution isn't to remove backoff. It's to add jitter." Full Jitter = `sleep = random(0, min(cap, base * 2 ^ attempt))` |
| Reactor 의 `Retry.backoff` 가 무작위를 기본으로 넣는다 | [RetryBackoffSpec](https://projectreactor.io/docs/core/release/api/reactor/util/retry/RetryBackoffSpec.html): "Retry delays are randomized with a user-provided jitter(double) factor between 0.d (no jitter) and 1.0 (default is 0.5)." |
| 그 대신 최대 대기 시간은 기본값이 사실상 없다 | 같은 곳, `maxBackoff`: "Defaults to Duration.ofMillis(Long.MAX_VALUE)." |
| 어떤 오류를 재시도할지는 조건자로 고른다 | 같은 곳, `filter`: "Set the Predicate that will filter which errors can be retried." / "Defaults to allowing retries for all exceptions." |
| 재고·요금 조회는 `GET` 이라 여러 번 불러도 같다 | 공급사 스펙의 두 API 모두 `GET` |

## Options

무엇을 재시도할지 가리는 방법
- **(가) 실패 신호에 "다시 불러 볼 여지가 있는가"를 담는다** ← 채택 — 예외 타입이 하나로 남아 ADR-0027 의 "consumer 에게 같은 신호로 보인다"가 깨지지 않는다.
  대신 실패 신호가 커지고, 그 값을 채우는 자리가 둘로 나뉜다
- **(나) 예외 타입을 나눈다** — 타입만 보고 가른다. 대신 consumer 가 두 타입을 알아야 하고 위 문장을 실제로 깬다
- **(다) 재시도하지 않는다** — 지금 그대로다. 대신 ADR-0050 이 적은 "묶음 하나에 숙소 50개가 빠진다"가 남는다

## Decision

- **실패 신호에 `transient` 를 담는다.** 뜻은 "다시 불러 볼 여지가 있는 실패인가"다. 기본값은 **아니요**다. 모르면 재시도하지 않는 쪽이 안전하다
- **채우는 자리는 둘이다.** [ADR-0031](0031-supplier-adapter-boundaries.md) 이 그은 선과 같다

  | 어디서 | 무엇을 보고 |
  |---|---|
  | 어댑터 안 | 본문의 결과 코드. 코드 체계가 공급사마다 다르다 |
  | `asSupplierFailure` (공통) | HTTP 상태, 연결 실패, 타임아웃. 503 은 어느 공급사든 503 이다 |

- **무엇이 일시적인가**

  | 실패 | `transient` | 왜 |
  |---|---|---|
  | 5xx (`E5xx` 포함) | 예 | "지금은 안 된다"는 신호이고 순간적일 수 있다 |
  | 429 (`E429`, 호출 한도) | 예 | 기다렸다 다시 부르라는 뜻이다 |
  | 연결하지 못함 | 예 | 빠르게 실패하고, 공급사 재시작 같은 순간적 상황일 수 있다 |
  | 4xx (`E400`·`E401`) | 아니요 | 요청이나 인증이 잘못됐다. 같은 요청을 다시 보내면 같은 거절이다 |
  | **정한 시간 안에 오지 않음** | **아니요** | **"공급사가 느리다"는 신호다. 느린 공급사를 다시 불러도 느릴 가능성이 높다** |
  | 본문을 읽지 못함 | 아니요 | 응답 구조의 문제라 다시 불러도 같다 |
  | 항목 목록이 없음 | 아니요 | 위와 같다 |

- **재시도할지는 consumer 가 정한다.** 같은 실패 신호에 정책이 다르다
  - **검색**: 묶음 호출에 재시도를 건다. 일시적인 실패만, 지수 백오프에 무작위를 섞어서
  - **목록 동기화**: 걸지 않는다. [ADR-0019](0019-first-catalog-failure-no-special-handling.md) 가 이미 "별도 장치를 두지 않고 다음 주기로 회복"으로 정했다
- **최대 대기 시간을 반드시 건다.** 안 걸면 사실상 무한이다
- **라이브러리를 더하지 않는다.** Reactor 에 이미 있고 그것이 무작위를 기본으로 넣는다
- **값(시도 횟수·기준 대기·최대 대기)은 설정에 두고 지금은 임시값이다.** 근거를 만들어 정하는 것은 선택 항목을 마친 뒤에 한다

**이유**

- 재고·요금 조회는 `GET` 이라 여러 번 불러도 결과가 달라지지 않는다. 재시도가 안전한 전제가 갖춰져 있다
- **타임아웃을 뺀 이유가 값과 무관하다.** 처음에는 "검색 시간 한계 안에 못 들어간다"를 이유로 들었는데 그것은 값에 기댄 이유였다.
  값을 빼고 보면 더 나은 이유가 있다. 타임아웃은 공급사가 느리다는 신호이고, 느린 것을 다시 불러도 느리다. 값이 바뀌어도 이 판단은 바뀌지 않는다
- 재시도 정책을 실패 신호에 박지 않는다. 고객이 기다리는 검색과 배경에서 도는 동기화는 같은 실패를 다르게 다뤄야 한다

## Consequences

**얻는 것**
- 순간적인 실패 때문에 묶음 하나가 통째로 빠지는 일이 줄어든다 (ADR-0050 이 적은 잃는 것)
- 재시도 대상 판정이 한 곳에 모이고, 공급사가 늘어도 그 규칙이 갈라지지 않는다
- 라이브러리가 늘지 않는다

**잃는 것**
- **실패 신호가 커진다.** `SupplierResponseException` 이 성질 하나를 더 든다
- **재시도가 공급사 부하를 늘린다.** 공급사가 힘들 때 우리가 더 부른다. 무작위 백오프가 그것을 흩뜨리지만 없애지는 못한다.
  Google SRE 가 말한 재시도 비율 상한(10%)은 두지 않는다. 지금 그 비율을 셀 자리가 없다
- **검색 한 건이 길어질 수 있다.** 재시도하는 만큼 늘어난다. 시간 한계가 상한을 잡지만 그만큼 고객이 더 기다린다
- **값에 근거가 없다.** 시도 횟수만 Google SRE 를 빌렸고 나머지는 임시값이다

**넘기는 것**
- 값의 근거 (선택 항목을 마친 뒤)
- 재시도 비율 상한. 셀 자리가 생기면(Q18) 다시 본다
- 반복해서 실패하는 공급사를 아예 끊을지 (서킷 브레이커, 선택 항목)

## Discussion

- **AI 주장과 근거** — (가)를 권하면서 처음에는 성질의 이름을 `retryable` 로 하려 했다
- **반박** — 사용자가 "그 실패 신호는 어디서 나누어서 담는 거야? 어댑터?" 라고 물었다
- **검증 결과** — 코드를 보니 어댑터 한 곳이 아니라 둘이었다. 그리고 그 질문을 따라가다 이름이 틀렸다는 것이 드러났다.
  **재시도할지는 실패의 성질이 아니라 부르는 쪽의 사정이다.** 검색과 동기화가 같은 실패를 다르게 다뤄야 하므로,
  실패 신호는 "일시적인가"까지만 말하고 재시도 여부는 consumer 가 정한다
- **또 하나** — 사용자가 "예산이라는 게 뭐야? 사용자의 인내심이야?" 라고 물어, AI 가 budget 을 "예산"으로 옮겨 쓰고 있었다는 것과
  검색 시간 한계 값에 근거가 없다는 것이 함께 드러났다. 그 질문이 타임아웃을 재시도에서 빼는 이유도 바꿨다(위 참조)
- **사용자 판단** — 값은 선택 항목을 마친 뒤에 튜닝하기로 하고, 구현 방식과 설계를 먼저 정했다

## Verification

실패가 일시적인지 아닌지 갈리는지
  → `SupplierAvailabilityAdapterTest.공급사가 5xx 로 알린 실패는 다시 불러 볼 여지가 있다`
  → `SupplierAvailabilityAdapterTest.호출 한도 초과도 여지가 있다`
  → `SupplierAvailabilityAdapterTest.잘못된 요청은 여지가 없다`
  → `SupplierAvailabilityAdapterTest.정한 시간 안에 오지 않은 것은 여지가 없다`
  → `SupplierAvailabilityAdapterTest.공급사 B 의 결과 코드도 갈린다` (본문 코드는 어댑터가 본다)

일시적인 실패만 다시 부르는지
  → `SearchServiceIntegrationTest.일시적인 실패는 다시 부르고 성공하면 그 결과를 쓴다`
  → `SearchServiceIntegrationTest.일시적이지 않은 실패는 다시 부르지 않는다`
  → `SearchServiceIntegrationTest.재시도를 0 으로 두면 한 번만 부른다`

다 써도 실패하면 정한 횟수에서 멈추고 감싸지 않은 원래 실패로 나가는지
  → `SearchServiceIntegrationTest.다시 불러도 계속 실패하면 정한 횟수에서 멈추고 원래 실패로 나간다`

목록 동기화는 다시 부르지 않는지
  → `app/src/main/kotlin/com/stayaggregator/catalog/CatalogSyncService.kt` 의 `sync` 에 `retryWhen` 이 없다
    (`grep -n "retryWhen" app/src/main/kotlin/com/stayaggregator/catalog/` 가 비어 있다)
