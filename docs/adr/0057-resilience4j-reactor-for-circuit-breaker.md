---
id: 0057
title: 서킷 브레이커는 resilience4j-reactor 를 쓰고 재시도·타임아웃은 Reactor 연산자로 두기로 결정
status: accepted
date: 2026-09-17
---

# 0057. 서킷 브레이커는 resilience4j-reactor 를 쓰고 재시도·타임아웃은 Reactor 연산자로 두기로 결정

## Context

[ADR-0056](0056-circuit-breaker-per-supplier-outside-retry.md) 이 서킷 브레이커를 어떻게 셀지 정했다. 이 ADR 은 무엇으로 만들지를 정한다.

재시도·타임아웃·동시 실행 수 제한은 이미 Reactor 연산자로 했다. 사용자가 이것이 "이미 있는 라이브러리를 두고 바퀴를 다시 만든 것으로 보이지 않을까"를 물었고,
그 답을 함께 남긴다. 확인한 사실은 [회복탄력성 라이브러리 비교](../research/resilience-library-comparison.md) 에 있다.

핵심 사실

| 사실 | 어떻게 확인했나 |
|---|---|
| resilience4j 의 재시도 연산자는 안에서 `Mono.retryWhen`, 타임아웃 연산자는 `Mono.timeout` 을 부른다 | javap |
| 서킷 브레이커는 Reactor 에 없다 | — |
| `resilience4j-spring-boot4` 는 Maven Central 에 없다. 최신 릴리스가 2.3.0 이다 | Maven Central 검색 |
| `resilience4j-reactor:2.3.0` 은 reactor-core 3.8.7 에서 컴파일되고 서킷이 열리는 것까지 실행된다 | 실행 |
| reactor 모듈의 서킷 연산자는 구독·성공·실패·**취소**마다 서킷에 알리고, 두 번 알리지 않도록 막는다 | javap |
| 우리 검색은 시간 한계를 넘기면 진행 중인 호출을 **취소한다** | `SearchService.searchSupplier` 의 `.timeout(search.budget)` |

## Options

서킷 브레이커
- **(가) `resilience4j-reactor`** ← 채택 — `Mono` 에 연산자 하나를 붙인다. 취소 때 허가를 돌려주는 일까지 라이브러리가 한다.
  대신 쓰지 않을 모듈 다섯(retry·timelimiter·bulkhead·ratelimiter·micrometer)이 runtime 으로 함께 들어온다
- **(나) `resilience4j-circuitbreaker` 코어만** — 의존성이 가장 가볍다.
  대신 허가 받기, 성공·실패 알리기, 걸린 시간, 취소 처리, 한 번만 알리기를 직접 연결한다. 우리 코드는 취소를 실제로 일으킨다
- **(다) 직접 구현** — 상태 기계와 동시성까지 직접 만든다. 검증된 코드를 두고 다시 만드는 것이다

재시도·타임아웃·동시 실행 수 제한
- **(ㄱ) Reactor 연산자로 둔다** ← 채택
- **(ㄴ) resilience4j 로 옮긴다** — 한 방식으로 모인다. 대신 동작은 같고 한 겹이 늘며, 동시 실행 수 제한은 기다림이 거절로 바뀐다

## Decision

- **서킷 브레이커는 `resilience4j-reactor` 의 연산자를 쓴다**
- **재시도·타임아웃·동시 실행 수 제한은 Reactor 연산자로 둔다.** resilience4j 로 옮기지 않는다
- 원칙은 하나다. **이미 쓰는 라이브러리에 있으면 그것을 쓰고, 없으면 완성된 라이브러리를 더한다. 직접 만들지 않는다**
- 설정은 Spring Boot 모듈이 없으므로 `StayProperties` 에서 읽어 서킷을 만든다

**이유**

- 재시도·타임아웃은 직접 만든 것이 아니다. Reactor 가 구현한 연산자를 쓴 것이고, resilience4j 도 안에서 같은 연산자를 부른다. 옮겨도 새 동작은 생기지 않는다
- 서킷은 Reactor 에 없어 라이브러리를 더한다. 코어만 쓰면 취소 처리를 직접 짜야 하는데 그 취소가 우리 코드에서 실제로 일어난다. 틀리기 쉬운 곳을 라이브러리에 맡긴다
- 동시 실행 수 제한을 resilience4j 로 옮기면 한도를 넘은 묶음이 기다리지 않고 실패한다. [ADR-0050](0050-partial-chunk-failure.md) 의 묶음 실패에 한도 초과가 섞인다
- resilience4j 가 주는 설정 파일 연동과 지표 엔드포인트는 Boot 4 모듈이 없어 어차피 쓸 수 없다

## Consequences

**얻는 것**
- 서킷의 상태 전이·동시성·취소 처리를 검증된 코드가 맡는다
- 재시도·타임아웃 코드는 그대로다
- "왜 두 방식을 섞었나"에 근거가 있다

**잃는 것**
- 쓰지 않을 resilience4j 모듈 다섯이 함께 들어온다
- 회복탄력성 기능이 두 라이브러리에 나뉜다. 읽는 사람이 둘을 알아야 한다
- 지표가 필요해지면 재시도·타임아웃 쪽은 직접 계측해야 한다

**다시 볼 것**
- `resilience4j-spring-boot4` 가 릴리스되면 설정·지표 연동을 다시 본다
- 지표(Q18)가 필요해지거나 공급사별 동시 호출 한도를 여러 검색 요청이 함께 써야 하면 재시도·동시 실행 수 제한을 resilience4j 로 옮길지 다시 본다

## Discussion

- **AI 주장과 근거 (1차)** — 서킷은 코어만 쓰자고 권했다. 근거는 `-reactor` 모듈이 reactor 3.4 기준이고 안 쓸 모듈을 끈다는 것이었다
- **사용자 판단 (1차)** — 코어만 쓰기로 정했다
- **다시 연 이유** — 사용자가 "바퀴 재사용으로 보일까 봐" 비교를 맡겼고, 비교 결과 reactor 모듈이 우리 reactor 3.8.7 에서 실행되는 것과 서킷 연산자가 취소까지 처리한다는 것이 드러났다.
  AI 가 1차에 든 반대 근거(3.4 기준)는 실행으로 해소됐고, 코어만 쓸 때 우리가 떠안을 취소 처리는 그때 보지 못했다
- **사용자 판단 (2차)** — "그냥 라이브러리 써. 기능적으로 완성된 라이브러리를 쓰는 게 낫지. 굳이 바퀴 또 만들 이유 없잖아?"

## Verification

구현할 때 확인하고 테스트 이름을 여기에 적는다.

- 서킷이 열리는지(ADR-0056 의 Verification 과 같은 테스트)
- 의존성을 넣은 뒤 `slf4j-api` 가 Boot 가 관리하는 2.0.x 로 풀리는지 (`./gradlew :app:dependencies --configuration runtimeClasspath`)
