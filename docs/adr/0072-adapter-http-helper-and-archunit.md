---
id: 0072
title: 어댑터의 HTTP 호출 조각을 한 클래스로 고정하고 어댑터 규칙을 기동 검사와 ArchUnit 테스트로 지키기로 결정
status: accepted
date: 2026-09-17
---

# 0072. 어댑터의 HTTP 호출 조각을 한 클래스로 고정하고 어댑터 규칙을 기동 검사와 ArchUnit 테스트로 지키기로 결정

## Context

독립 설계 검토(다른 모델, 2026-09-17)가 "새 공급사가 빠뜨려도 조용히 통과하는 것" 셋을 짚었다.

1. **`SupplierAdapter` 는 강제가 아니었다.** [ADR-0031](0031-supplier-adapter-boundaries.md) 과 README 는 "한 인터페이스를 빠뜨리면 컴파일에서 드러난다"고 적었지만, `class SupplierCAdapter : CatalogAdapter` 라고만 써도 컴파일·기동·테스트가 통과하고 C 는 검색에서 조용히 빠진다. 관례였지 타입이 지키는 것이 아니었다. 설정 블록만 있고 어댑터가 없는 경우도 조용히 무시됐다
2. **어댑터마다 `.bodyToMono().timeout().asSupplierFailure()` 를 손으로 붙였다.** [ADR-0066](0066-connect-timeout-in-shared-webclient.md) 이 연결 타임아웃을 한 함수로 모은 논리가 호출 타임아웃과 실패 변환에는 적용되지 않았다. 새 어댑터가 `.timeout()` 을 빠뜨리면 그 호출은 검색 전체 타임아웃까지 기다리고 서킷이 세지 못한다. `.asSupplierFailure()` 를 빠뜨리면 지표가 내부 오류로 세고 서킷이 무시한다
3. **인증이 API 키 헤더 하나로 고정**되어 있었다. 다른 방식의 공급사가 오면 `supplierWebClient` 안에 분기가 생기거나 우회하게 된다

패키지 의존 방향([ADR-0040](0040-package-structure.md), [ADR-0069](0069-domain-package.md))도 검토 때 import 를 눈으로 보고 지켰다. ADR-0040 은 "도구로 강제하는 것은 필요해질 때"라고 미뤘다.

## Options

HTTP 호출 조각
- **(가) 합성 헬퍼 `SupplierHttp`** ← 채택 — 어댑터가 하나씩 들고, 목록·재고 호출 메서드 둘이 순서를 고정한다. 어댑터는 경로와 쿼리, DTO 타입만 준다. 대신 `WebClient` 를 어댑터가 직접 못 만져 다른 HTTP 메서드가 필요한 공급사가 오면 헬퍼에 메서드를 더한다
- **(나) 추상 부모 클래스(Template Method)** — 순서를 부모가 고정한다. 대신 상속이 호출 순서를 부모에 숨기고, 어댑터가 부모의 구현에 묶인다
- **(다) 확장 함수 하나** — 가장 작다. 대신 어댑터가 그 함수를 부르지 않아도 되므로 빠뜨림을 막지 못한다

어댑터 규칙 강제
- **(ㄱ) 기동 검사 + ArchUnit 테스트** ← 채택 — 기동 검사는 두 인터페이스 목록과 설정 키 집합이 같은지 본다(실행 환경에서도 잡힘). ArchUnit 은 컴파일된 클래스의 의존을 읽어 패키지 방향·어댑터 규칙·`WebClient.builder()` 위치를 본다(테스트에서 잡힘). 대신 테스트 의존성이 하나 는다
- **(ㄴ) grep 검사 유지** — 의존성이 없다. 대신 import 문자열만 보고 실제 의존은 모른다
- **(ㄷ) Spring Modulith** — 모듈 구조 검증을 준다. 대신 런타임 라이브러리이고 모듈 구조 규약을 따라야 해서, 검증만 필요한 지금은 무겁다

## Decision

- `supplier.SupplierHttp` 가 `bodyToMono → timeout → asSupplierFailure` 순서를 고정한다. 어댑터는 `getCatalog(path, DTO)`·`getAvailability(DTO) { uri }` 를 부르고 `.map` 으로 `Fetched…` 를 만든다
- 인증은 `ExchangeFilterFunction` 으로 받는다. API 키 헤더는 `apiKeyHeader(name, key)` 다. 다른 방식이 오면 필터를 하나 더 두고 어댑터가 고른다
- `SupplierRegistrationCheck` 가 기동 때 `CatalogAdapter` 목록, `AvailabilityAdapter` 목록, 설정 블록 키 집합이 모두 같은지 검사한다. 다르면 기동에 실패한다
- `ArchitectureTest`(ArchUnit 1.4.1) 가 규칙 일곱을 지킨다. 패키지 방향 넷(ADR-0069 의 문장), 공급사 하위 패키지의 어댑터는 `SupplierAdapter` 를 구현, `WebClient.builder()` 는 `SupplierWebClient.kt` 에서만, 어댑터는 `WebClient` 를 직접 쓰지 않음
- ADR-0031·README·용어집의 "컴파일에서 드러난다"는 문장을 사실대로 고친다

**이유**
- 세 문제의 공통점은 "빠뜨려도 조용하다"이다. 조용한 실패는 문서가 아니라 검사로 막는다
- ArchUnit 을 지금 들이는 이유는 구현체가 늘 때 규칙을 사람 눈이 아니라 테스트로 지키기 위해서다. ADR-0040 이 "필요해질 때"라고 한 그때가 공급사 셋째가 아니라 지금이라고 봤다. 규칙을 어긴 클래스를 일부러 만들어 네 규칙이 실제로 실패하는 것을 확인했다

## Consequences

**얻는 것**
- 새 어댑터가 타임아웃·실패 변환·연결 설정을 빠뜨릴 자리가 없다
- 한 인터페이스만 구현하거나 설정과 어긋난 공급사는 기동과 테스트에서 드러난다
- 패키지 방향이 문서가 아니라 테스트로 남는다

**잃는 것**
- 테스트 의존성 하나(`archunit-junit5`)
- 클래스 셋(`SupplierHttp`, `SupplierRegistrationCheck`, `ArchitectureTest`)
- `GET` 이 아닌 호출이 필요한 공급사가 오면 `SupplierHttp` 에 메서드를 더해야 한다

## Discussion

- **AI 주장과 근거** — 독립 설계 검토가 헬퍼(합성)와 기동 검사·ArchUnit 을 권했다. AI 가 ArchUnit 도입은 의존성 결정이라 사용자에게 물었다
- **사용자 판단** — "확실히 도움될 거 같다"며 도입하기로 했다

## Verification

호출 타임아웃·실패 변환이 헬퍼를 거쳐도 전과 같이 동작하는지
  → `SupplierAvailabilityAdapterTest`·`SupplierCatalogAdapterTest` 전부가 그대로 통과한다

한 인터페이스만 구현하거나 설정과 어긋나면 기동에 실패하는지
  → `SupplierRegistrationCheckTest.한 인터페이스만 구현한 공급사가 있으면 기동에 실패한다`
  → `SupplierRegistrationCheckTest.설정 블록만 있고 어댑터가 없는 공급사가 있으면 기동에 실패한다`

규칙이 실제로 잡는지
  → `ArchitectureTest` 일곱 규칙 통과. `supplier.c` 에 `CatalogAdapter` 만 구현하고 `WebClient.builder()` 를 부르며 `search` 를 import 하는 클래스를 넣었을 때
    `공급사 어댑터는 두 인터페이스를 함께 구현한다`, `WebClient 는 SupplierWebClient 에서만 만든다`, `어댑터는 HTTP 응답을 직접 읽지 않는다`, `supplier·mapping·quarantine 은 domain 만 본다` 네 규칙이 실패하는 것을 확인하고 지웠다 (2026-09-17)
