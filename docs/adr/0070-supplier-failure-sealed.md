---
id: 0070
title: 공급사 실패 예외를 boolean 성질 대신 sealed 하위 타입으로 나누기로 결정
status: accepted
date: 2026-09-17
---

# 0070. 공급사 실패 예외를 boolean 성질 대신 sealed 하위 타입으로 나누기로 결정

## Context

공급사 실패 예외 하나(`SupplierResponseException`)가 성질을 boolean 으로 들고 있었다.

| 성질 | 더한 결정 | 해석하는 곳 |
|---|---|---|
| `transient` (재시도 가능) | [ADR-0051](0051-retry-transient-supplier-failures.md) | 검색의 재시도 조건, 어댑터 B, `asSupplierFailure` |
| `timedOut` | [ADR-0060](0060-supplier-call-metrics.md) | 지표의 결과 태그 |
| `throttled` (요청 한도 초과) | [ADR-0068](0068-search-values-from-relations.md) | 검색의 재시도 기준 선택 |

ADR-0051 은 "예외 타입을 나눈다"(나)를 검토하고 "consumer 가 두 타입을 알아야 하고 ADR-0027 의 '같은 예외로 보인다'를 깬다"는 이유로 버렸다.
그리고 잃는 것에 "공급사 실패 예외가 커진다"를 적었다. 그 뒤 성질이 둘 더 붙어 셋이 됐다. 예측한 비용이 현실이 됐다.

독립 설계 검토(다른 모델, 2026-09-17)가 짚은 것: 성질을 해석하는 곳이 다섯이고 모두 `if` 사슬이라, 종류가 하나 더 늘어도(예: 인증 실패 → 재시도 안 함 + 경보) 빠뜨린 곳을 컴파일러가 알리지 않는다.
그리고 **sealed 계층은 루트 타입이 하나라 ADR-0051 이 (나)를 버린 이유가 성립하지 않는다.** consumer 는 루트만 잡는다.

## Options

- **(가) sealed 계층으로 바꾼다** ← 채택 — `Timeout`, `Throttled`, `Unavailable`, `Rejected`, `Unreadable` 다섯 종류. 재시도 가능 여부는 종류가 정한다. 해석은 `when` 이라 종류가 늘면 컴파일이 알린다. 대신 만드는 곳이 종류를 골라야 하고, 테스트의 생성 지점을 고친다
- **(나) boolean 을 유지한다** — 바꿀 것이 없다. 대신 넷째 boolean 이 붙는 순간 조합이 8가지가 되고 뜻이 없는 조합(타임아웃이면서 요청 한도 초과)도 만들 수 있다

## Decision

- 이름을 `SupplierFailure` 로 바꾸고 sealed class 로 한다. 종류와 재시도 가능 여부는 이렇다

  | 종류 | 무엇 | 재시도 |
  |---|---|---|
  | `Timeout` | 우리가 건 타임아웃, Netty 연결·읽기 타임아웃 | 아니요 |
  | `Throttled` | HTTP 429, B 의 `E429` | 예 (다른 기준, ADR-0068) |
  | `Unavailable` | 5xx, B 의 `E5xx`, 연결 거절 | 예 |
  | `Rejected` | 그 밖의 4xx, B 의 `E400`·`E401`, 스펙에 없는 결과 코드 | 아니요 |
  | `Unreadable` | 본문 해석 실패, 목록 필드 없음, 빈 응답 | 아니요 |

- consumer(검색, 목록 동기화, 서킷의 `ignoreException`)는 루트 `SupplierFailure` 만 잡는다. ADR-0027 의 "한 가지 실패"는 그대로다
- 종류를 고르는 곳은 ADR-0051 이 정한 둘 그대로다. HTTP 계층은 `asSupplierFailure`, 본문 결과 코드는 어댑터
- `transient` 는 추상 속성으로 남긴다. 재시도 조건이 종류 이름이 아니라 성질을 보게 두어, 종류가 늘 때 재시도 조건을 고치지 않는다

## Consequences

**얻는 것**
- 지표·재시도·로그가 `when` 으로 갈라, 종류가 늘면 빠뜨린 곳이 컴파일 오류로 드러난다
- 뜻이 없는 성질 조합을 만들 수 없다

**잃는 것**
- 만드는 쪽이 종류를 골라야 한다. 어댑터 B 의 결과 코드 분기가 세 갈래가 됐다
- 테스트의 예외 생성 지점 20여 곳을 고쳤다

**다시 볼 것**
- 인증 실패(401, `E401`)를 `Rejected` 와 나눠 경보 대상으로 둘지. 지금은 요구가 없다

## Discussion

- **AI 주장과 근거** — 독립 설계 검토가 "지금 고칠 것"으로 권했다. ADR-0051 의 기각 이유가 sealed 에는 해당하지 않는다는 지적이 맞았다
- **사용자 판단** — 설계 검토의 "지금 고칠 것" 아홉 항목을 그대로 진행하기로 했다

## Verification

HTTP 실패가 종류로 갈리는지
  → `SupplierAvailabilityAdapterTest.공급사가 5xx 로 알린 실패는 재시도 가능하다` 등 기존 테스트가 하위 타입으로 단언한다
  → `SupplierCallMetricsTest.실패 종류마다 결과 태그가 다르다`

B 의 결과 코드가 종류로 갈리는지
  → `SupplierAvailabilityAdapterTest.공급사 B 의 결과 코드도 재시도 가능 여부가 구분된다`

요청 한도 초과가 다른 기준으로 재시도되는지
  → `SearchServiceIntegrationTest` 의 429 재시도 테스트 (ADR-0068 Verification)
