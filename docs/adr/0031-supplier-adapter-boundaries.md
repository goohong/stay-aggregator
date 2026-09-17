---
id: 0031
title: 공급사 어댑터를 기능별 경계로 나누고 공급사마다 한 클래스로 구현하기로 결정
status: accepted
date: 2026-09-16
---

# 0031. 공급사 어댑터를 기능별 경계로 나누고 공급사마다 한 클래스로 구현하기로 결정

## Context

공급사마다 API 가 두 가지다. 숙소 목록 조회는 목록 동기화가 쓰고([ADR-0013](0013-catalog-sync-on-startup-and-interval.md)),
재고·요금 조회는 검색이 쓴다([ADR-0009](0009-reactor-over-coroutines.md)). 목록이 있어야 재고를 물을 수 있으므로 공급사마다 두 기능이 항상 함께 필요하다.
요구사항은 공급사별 요청·응답 형식이 도메인 계층으로 새지 않게 경계를 두고, 새 공급사를 추가할 때 무엇을 고쳐야 하는지 문서로 설명하라고 한다.

두 consumer 는 다루는 방식이 다르다. 목록 동기화는 실패해도 다음 주기까지 그 공급사가 빠질 뿐이고([ADR-0019](0019-first-catalog-failure-no-special-handling.md)),
검색은 부분 실패 표시·타임아웃·동시 호출 수 제한을 다룬다. [ADR-0014](0014-detect-drift-in-search-correct-in-sync.md) 는 **고객 검색이 목록 갱신을 일으키지 않는다**고 정했다.

확인한 사실

| 사실 | 원문 |
|---|---|
| WebClient 의 응답 처리 메서드는 모두 `Mono` 또는 `Flux` 를 돌려준다 | [WebClient.ResponseSpec Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/reactive/function/client/WebClient.ResponseSpec.html) |
| 특정 타입의 빈을 한꺼번에 주입받을 수 있다. 한 빈이 두 인터페이스를 구현하면 양쪽 목록에 들어간다 | [Spring Framework, Using @Autowired](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html#:~:text=provide%20all%20beans%20of%20a%20particular%20type%20from%20the%20ApplicationContext): "provide all beans of a particular type from the ApplicationContext by adding the @Autowired annotation to a field or method that expects an array of that type" |
| 그 목록의 순서는 `@Order` 가 없으면 빈 등록 순서다 | 같은 절: "their order follows the registration order of the corresponding target bean definitions in the container" |
| 같은 멤버의 구현을 여러 상위에서 물려받을 때만 override 가 강제된다. 추상 멤버만 있으면 한 번만 구현하면 된다 | [Kotlin, Overriding rules](https://kotlinlang.org/docs/inheritance.html#overriding-rules): "if a class inherits multiple implementations of the same member from its immediate superclasses, it must override this member" |

## Options

어댑터 단위
- **(가) 공급사마다 인터페이스 하나가 두 기능을 모두 가진다** — 공급사당 클래스 하나. 대신 검색 코드가 목록 조회 기능까지 보게 되어 ADR-0014 를 코드가 막지 못하고, consumer 테스트의 가짜 구현이 쓰지 않는 기능을 채워야 한다.
- **(나) 기능마다 인터페이스를 두고 공급사마다 클래스도 둘로 나눈다** — consumer 가 좁게 본다. 대신 한 기능만 등록해도 컴파일과 기동이 통과해 그 공급사가 조용히 빠지고(ADR-0019 로 알려 주는 곳도 없다), 공급사 하나의 주소·키·WebClient 설정이 두 클래스로 갈라진다.
- **(다) 기능마다 인터페이스를 두되 공급사마다 한 클래스가 둘을 함께 구현한다** ← AI 추천 — consumer 는 좁게 보고 공급사는 한 곳이다. 대신 기억할 이름이 늘고, 한 클래스가 커질 수 있다.

어댑터의 책임 범위
- **(가) 호출·수신·표준 모델 변환·검증까지 전부** — 대신 모든 공급사에 같은 규칙([ADR-0023](0023-multi-night-availability-minimum.md), [ADR-0027](0027-spec-violation-handling-criteria.md))을 공급사마다 다시 구현한다.
- **(나) 공급사마다 다른 것까지만** ← AI 추천 — 공통 규칙은 한 곳에만 있다. 대신 어댑터와 공통 로직 사이의 공통 형태를 정의해야 한다.
- **(다) 호출과 DTO 수신만** — 공급사 DTO 가 어댑터 밖으로 나가 요구사항의 경계를 어긴다.

목록 조회의 반환 타입
- **(가) `Mono`** ← AI 추천 — 한 클래스 안에서 두 기능의 모양이 같고, 타임아웃을 같은 방식으로 걸고, 공급사들을 함께 호출할 수 있다. 대신 기다리는 사람이 없는 배경 작업에 Reactor 개념이 들어간다.
- **(나) 값을 바로 돌려줌** — 어댑터 안에서 기다린다. 대신 한 클래스 안에서 스타일이 갈리고 기다리는 자리가 숨는다.

등록 방식
- **(가) 인터페이스 타입으로 선언한 목록으로 한꺼번에 주입** ← AI 추천 — 새 공급사는 클래스만 추가하면 된다. 대신 목록 순서에 기대면 안 된다.
- **(나) 공급사별 구체 타입을 하나씩 주입** — consumer 가 공급사 클래스를 알게 되고, 공급사를 추가할 때 consumer 도 고친다.
- **(다) 설정에 나열한 공급사만 등록** — 공급사를 끄는 스위치가 생긴다. 대신 설정과 코드를 맞춰야 하고, 끄는 요구는 확인되지 않았다.

## Decision

- 기능마다 경계를 둔다. 숙소 목록 조회용 경계와 재고·요금 조회용 경계(`RateAdapter`, ADR-0009)를 나눈다
- 공급사마다 **한 클래스**가 두 경계를 함께 구현한다. 두 경계를 묶은 인터페이스를 두어, 한 기능을 빠뜨리면 컴파일에서 드러나게 한다
- 어댑터는 호출, DTO 수신, **공급사마다 다른 것**(실패 표현의 차이, 필드 이름, 요금 구조)을 공통 형태로 바꾸는 데까지 한다. 모든 공급사에 같은 규칙(ADR-0023 의 최솟값, ADR-0027 의 날짜 목록 위반)은 어댑터 밖 공통 로직에 둔다
- 목록 조회도 `Mono` 를 돌려준다. 기다리는 자리는 동기화 서비스다
- consumer 는 인터페이스 타입으로 선언한 목록으로 어댑터를 한꺼번에 주입받는다. 목록의 순서에 기대지 않는다
- 구현은 필요한 경계부터 붙인다. 목록 동기화 단위에서는 목록 조회 경계만 만들고, 재고·요금 경계와 묶는 인터페이스는 요금(Q1)·날짜 구간(Q2)이 정해지는 검색 단위에서 더한다. 그 자리에 무엇이 붙는지는 주석으로 남긴다

**이유** — 두 consumer 는 서로 다른 결정 묶음을 갖고 있고, 검색이 목록 갱신을 부르지 않는다는 ADR-0014 를 좁은 경계가 타입으로 강제한다.
공급사마다 한 클래스면 주소·키·WebClient 설정이 한 곳에 모이고, 한 기능을 빠뜨려 그 공급사가 조용히 빠지는 일을 묶는 인터페이스가 막는다.
같은 규칙을 공급사마다 구현하면 공급사가 늘수록 규칙이 어긋날 곳이 늘어난다.

## Consequences

**얻는 것**
- 검색 코드에서 목록 조회를 부르는 코드가 컴파일되지 않는다
- consumer 테스트의 가짜 구현이 쓰지 않는 기능을 채우지 않는다
- 새 공급사를 추가할 때 고칠 곳이 "어댑터 클래스 하나, 그 공급사 DTO, 설정"으로 좁다
- 공통 규칙이 한 곳에 있어 규칙을 바꿀 때 한 곳만 고친다

**잃는 것**
- 기억할 이름이 셋(목록 경계, 재고 경계, 묶는 경계)이다
- 목록 동기화 단위 동안에는 묶는 인터페이스가 없어 그 보호가 없다. 두 공급사 클래스를 처음 만드는 시점이라 빠뜨릴 기능이 없다는 판단이다
- 어댑터와 공통 로직 사이의 공통 형태를 정의해야 하고, 재고·요금 쪽 공통 형태는 요금(Q1) 뒤에 정해진다
- ADR-0027 의 첫 질문(응답 전체를 읽을 수 있나)은 실패 표현이 공급사마다 달라 어댑터에서, 두세 번째 질문은 공통 로직에서 답한다. 검증 기준이 두 곳에 나뉜다
  - 첫 질문 중 **목록을 담는 필드가 있는지**는 공급사마다 다르지 않아 [ADR-0041](0041-validate-in-constructors.md) 에서 공통 로직으로 옮겼다. 어댑터에 남은 것은 HTTP 상태·결과 코드·본문 읽기다

**넘기는 것**
- 공급사별 타임아웃·동시 호출 수 설정을 어댑터에 어떻게 넘길지 (Q23)
- 검색 결과를 합치는 순서. 주입 목록의 순서에 기대지 않으므로 필요하면 합치는 단계에서 정한다
- ~~공급사 식별자를 공통 형태에도 실을지~~ → [ADR-0049](0049-supplier-id-on-adapter-only.md) 에서 정했다 (2026-09-17). 공통 형태에서 빼고 어댑터만 갖는다

## Discussion

- **AI 주장과 근거** — 기능별 경계와 공급사당 한 클래스를 권하면서, 약점을 "공급사가 둘이라 좁은 경계가 문제를 막는 일이 드물다"로 들고 묶는 인터페이스는 선택으로 뒀다
- **반박** — 사용자가 판단이 어렵다며 독립 검토를 요청했고, 검토는 세 가지를 짚었다. 좁은 경계의 역할은 버그 방지가 아니라 consumer 와 테스트 대역의 의존 모양이며 그 효과는 공급사 수가 아니라 consumer 수에 비례한다.
  ADR-0014 를 타입으로 강제하는 것이 구체적인 이점이다. 한 기능을 빠뜨리면 ADR-0019 때문에 공급사가 조용히 빠지므로 묶는 인터페이스는 선택이 아니다
- **검증 결과** — 지적이 맞다. 인용한 Spring·Kotlin 문서를 원문과 대조했고, 검토가 든 Spring 주입 동작도 확인했다. AI 가 (나)의 약점으로 적은 "설정이 갈라짐"보다 조용한 등록 누락이 더 무거웠다
- **그래서 어떻게 바뀌었나** — 약점을 "어댑터 클래스가 커질 수 있고 공통 형태가 Q1 에 달려 있다"로 옮기고, 묶는 인터페이스를 결정에 포함했다

## Verification

공급사를 하나 더 추가할 때 consumer 코드를 고치지 않아도 되는지
  → `CatalogSyncIntegrationTest.공급사를 하나 더 붙여도 consumer 코드를 고치지 않는다`

목록 동기화 서비스가 목록 조회 인터페이스의 목록을 주입받는지
  → `app/src/main/kotlin/com/stayaggregator/catalog/CatalogSyncService.kt` 의 생성자

공급사 클래스가 두 경계를 함께 구현하는지
  → `SupplierAAdapter`·`SupplierBAdapter` 가 `SupplierAdapter`(= `CatalogAdapter` + `AvailabilityAdapter`) 를 구현한다

어댑터가 요금을 더하지 않고 공급사가 준 모양 그대로 넘기는지
  → `SupplierAvailabilityAdapterTest.공급사 A 의 날짜별 요금과 재고를 갈라서 그대로 넘긴다`
  → `SupplierAvailabilityAdapterTest.공급사 B 의 총액 요금을 그대로 넘긴다`

두 경계가 실패를 같은 신호로 바꾸는지
  → `SupplierAvailabilityAdapterTest.공급사 B 가 본문 결과 코드로 알린 실패는 목록 경계와 같은 오류가 된다`

검색 서비스가 재고 조회 인터페이스만 주입받는지
  → `SearchServiceIntegrationTest.검색 서비스는 재고 조회 인터페이스의 목록만 주입받는다`
