---
id: 0071
title: 재시도·서킷·지표를 검색 서비스가 아니라 어댑터를 감싸는 Decorator 에 두기로 결정
status: accepted
date: 2026-09-17
---

# 0071. 재시도·서킷·지표를 검색 서비스가 아니라 어댑터를 감싸는 Decorator 에 두기로 결정

## Context

공급사 호출 하나를 둘러싼 정책이 세 층에 나뉘어 있었다.

| 층 | 무엇 |
|---|---|
| 어댑터 안 | 호출 타임아웃, 실패 변환 (`asSupplierFailure`) |
| `SearchService.fetchChunk` | 재시도([ADR-0051](0051-retry-transient-supplier-failures.md)), 서킷([ADR-0056](0056-circuit-breaker-per-supplier-outside-retry.md)), 지표([ADR-0060](0060-supplier-call-metrics.md)) |
| `CatalogSyncService` | 지표만 손으로 한 벌 더 |

서킷 객체(`SupplierCircuitBreakers`)도 `search` 패키지에 있었다.

예약 대행 설계([ADR-0064](0064-reservation-proxy-design-only.md))는 예약 직전에 재고·요금을 다시 확인하고, 서킷이 열린 공급사에는 보내지 않는다고 했다. 그 호출은 검색과 같은 재시도·서킷·지표를 거쳐야 한다.
지금 구조에서 예약 대행은 `search` 를 import 하고 `fetchChunk` 의 연산자 사슬을 **복사**하게 된다. 재시도 기준을 바꾸면 두 곳이 갈린다.

독립 설계 검토(다른 모델, 2026-09-17)가 이것을 가장 큰 약점으로 꼽았다. "구현체가 늘 때 유지보수·디자인 패턴 관점"이라는 이전 피드백이 정확히 이 지점이라고 했다.
ADR-0057 은 라이브러리 선택만 다뤘고 **어디에 두는가**는 다루지 않았다. ADR-0064 는 "서킷을 예약에도 둘지"를 다시 볼 것으로만 남겼다.

## Options

- **(가) 어댑터를 감싸는 Decorator** ← 채택 — `ResilientAvailabilityAdapter` 가 `AvailabilityAdapter` 를 구현하고 안에 원본을 든다. consumer 는 감싸진 것을 받았다는 사실을 모른다. 정책이 한 곳이다. 대신 감싸는 조립 코드가 한 곳 생기고, 원본 어댑터도 빈이라 `List<AvailabilityAdapter>` 를 그대로 주입받으면 감싸지 않은 것이 섞이는 문제를 풀어야 한다
- **(나) 정책을 담은 서비스 하나를 두고 consumer 가 그것을 통해 호출한다** — 감싸는 문제가 없다. 대신 consumer 가 어댑터 대신 그 서비스를 알아야 해서, 어댑터 인터페이스가 consumer 쪽 계약이라는 ADR-0031 의 구조가 흐려진다
- **(다) 지금대로 두고 예약 대행 때 복사한다** — 바꿀 것이 없다. 대신 정책이 두 벌이 된다

## Decision

- `supplier` 패키지에 `ResilientAvailabilityAdapter` 를 둔다. 안에서 밖으로 **재시도 → 서킷 → 지표** 순서다. 순서의 근거는 ADR-0056·0060 그대로다
- 서킷이 거절한 것은 resilience4j 의 `CallNotPermittedException` 이 아니라 `SupplierFailure.CircuitOpen` 으로 바꿔 올린다. consumer 가 라이브러리 타입을 모르게 한다 ([ADR-0070](0070-supplier-failure-sealed.md) 의 종류가 여섯이 된다)
- 감싼 목록에 자기 타입 `ResilientAvailabilityAdapters` 를 준다. `List<AvailabilityAdapter>` 로 받으면 원본 빈이 섞이기 때문이다. 감싸는 곳은 `SupplierResilienceConfig` 한 곳이다
- `SupplierCircuitBreakers` 를 `supplier` 로 옮긴다. 서킷 상태는 "그 공급사가 지금 죽었나"이지 검색만의 것이 아니다
- `SearchService` 는 감싼 어댑터를 호출하고 실패한 chunk 를 결과로 바꾸는 일만 한다. 재시도·서킷·지표 코드가 없다
- 목록 동기화는 감싸지 않는다. 재시도하지 않고([ADR-0019](0019-first-catalog-failure-no-special-handling.md)) 서킷도 두지 않는다(ADR-0056). 지표는 지금처럼 `CatalogSyncService` 가 직접 센다

## Consequences

**얻는 것**
- 두 번째 consumer(예약 대행)가 같은 감싼 어댑터를 주입받으면 재시도·서킷·지표가 따라온다. 복사할 코드가 없다
- `SearchService` 가 Reactor 재시도·resilience4j 를 모른다. 검색 코드를 고치는 사람이 그 둘을 몰라도 된다
- 재시도·서킷 동작을 감싼 어댑터 단위로 시험할 자리가 생겼다

**잃는 것**
- 클래스가 셋 늘었다 (Decorator, 감싼 목록 타입, 조립 설정)
- 재시도·서킷 설정은 여전히 `stay.search.*` 아래다. 검색 전체 타임아웃과의 관계식을 설정 객체가 함께 검사하기 때문에 이번에 옮기지 않았다

**다시 볼 것**
- 공급사별로 재시도·서킷 기준이 달라져야 하면 설정을 `stay.suppliers.<id>` 아래로 옮기고 `wrap` 이 공급사별 값을 쓴다
- 목록 동기화의 지표를 같은 방식(지표만 붙이는 Decorator)으로 옮길지
- 재시도·서킷 통합 테스트(`SearchServiceIntegrationTest`)를 Decorator 단위 테스트로 옮겨 Postgres 없이 돌릴지

## Discussion

- **AI 주장과 근거** — 독립 설계 검토가 Decorator 를 권했다. AI 가 감싼 목록에 자기 타입을 주는 방식을 더했다. `@Qualifier` 로도 될 수 있으나 Spring 의 컬렉션 주입 규칙에 기대는 것보다 타입이 분명하다고 봤다
- **사용자 판단** — 설계 검토의 "지금 고칠 것" 아홉 항목을 그대로 진행하기로 했다

## Verification

감싼 어댑터를 통해서도 재시도·서킷·지표가 전과 같이 동작하는지
  → `SearchServiceIntegrationTest` 의 재시도·서킷·지표 테스트 전부가 감싼 어댑터로 바꾼 뒤 그대로 통과한다 (`./gradlew :app:test` 158건)

서킷이 거절한 호출이 우리 예외 종류로 오는지
  → `SupplierCallMetricsTest.실패 종류마다 결과 태그가 다르다` (`CircuitOpen` → `circuit_open`)

`SearchService` 에 resilience4j·Reactor 재시도 import 가 없는지
  → `grep -n "resilience4j\|retry" app/src/main/kotlin/com/stayaggregator/search/SearchService.kt` 가 아무것도 찾지 않는다 (2026-09-17 실행)
