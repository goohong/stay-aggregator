---
id: 0040
title: 패키지를 기능별로 나누고 의존 방향을 한 줄로 정하기로 결정
status: accepted
date: 2026-09-16
---

# 0040. 패키지를 기능별로 나누고 의존 방향을 한 줄로 정하기로 결정

## Context

목록 동기화 구현 단위가 끝나 파일이 생겼다. 지금은 `catalog` 안에 성격이 다른 넷이 함께 있다.
공급사와 만나는 인터페이스(`CatalogAdapter`), 공급사 응답의 공통 형태(`FetchedCatalog`), 공급사 실패 신호(`SupplierResponseException`),
매핑 테이블을 다루는 저장소(`MappingRepository`)다.

다음 구현 단위는 검색이고, 검색도 같은 매핑 테이블을 읽어야 한다. 요구사항은 새 공급사를 추가할 때 무엇을 고치는지
문서로 설명하라고 하므로, 그 답이 디렉터리에서 보이면 설명이 짧아진다.

확인한 사실

| 사실 | 원문 |
|---|---|
| 메인 클래스를 루트 패키지에 두면 그 아래가 스캔 범위가 된다 | [Spring Boot, Structuring Your Code](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html#:~:text=We%20generally%20recommend%20that%20you%20locate%20your%20main%20application%20class%20in%20a%20root%20package%20above%20other%20classes), 절 "Locating the Main Application Class": "We generally recommend that you locate your main application class in a root package above other classes." / "Using a root package also allows component scan to apply only on your project." |
| 같은 페이지의 예시 구조는 기능별이지만, 문서가 기능별을 권한다고 쓰지는 않는다. 구조를 강제하고 싶으면 따로 도구를 보라고 한다 | 같은 페이지 도입부(절 제목 앞): "If you wish to enforce a structure based on domains, take a look at Spring Modulith." |
| 설정 바인딩도 애노테이션을 선언한 클래스의 패키지부터 스캔한다 | [Spring Boot, Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.enabling-annotated-types), 절 "Enabling @ConfigurationProperties-annotated Types": "By default, scanning will occur from the package of the class that declares the annotation." |
| Kotlin 의 `internal` 은 패키지가 아니라 모듈 단위라, 패키지 사이의 의존 규칙을 컴파일러가 막지 못한다 | [Kotlin, Visibility modifiers](https://kotlinlang.org/docs/visibility-modifiers.html#modules): "a module is a set of Kotlin files compiled together" / "A Gradle source set" |

## Options

- **(가) `supplier` / `mapping` / `catalog` / `search`** ← 채택 — 공급사와 만나는 것은 `supplier`, 매핑 테이블을 아는 것은 `mapping`,
  두 흐름은 `catalog` 와 `search`. 대신 패키지가 하나 늘고 `mapping` 은 지금 파일이 넷뿐이다
- **(나) `supplier` / `catalog` / `search` / `config`** — 패키지가 적다. 대신 저장소가 `catalog` 에 남아 검색이 `catalog` 를 보게 되고,
  [ADR-0014](0014-detect-drift-in-search-correct-in-sync.md) 가 정한 "검색이 목록 갱신을 일으키지 않는다"를 패키지 방향이 거들지 못한다.
  `config` 에 넣을 파일도 지금은 없다
- **(다) 지금 구조를 두고 검색 단위에서 함께 정리** — 지금 움직이지 않는다. 대신 파일 이동과 새 코드가 한 diff 에 섞여 검토 단위가 커진다

## Decision

**(가)** 를 택한다. 패키지는 넷이고, 의존 방향은 한 줄이다.

**`catalog` 와 `search` 는 `supplier` 와 `mapping` 을 보고, 서로는 보지 않으며, `supplier` 와 `mapping` 은 아무것도 보지 않는다.**

| 패키지 | 무엇이 있나 |
|---|---|
| `supplier` | 공급사와 만나는 것 전부. 어댑터 인터페이스, 공급사 응답의 공통 형태, 공급사 실패 신호, 공급사 설정, 공급사별 하위 패키지(`a`, `b`) |
| `mapping` | 매핑 테이블을 아는 유일한 곳. 저장소와 저장소가 주고받는 형태 |
| `catalog` | 목록 동기화 흐름. 스케줄러, 동기화 서비스, 정규화 |
| `search` | 검색 흐름. 검색 구현 단위에서 만든다 |

**이유**

- 공급사 실패 신호와 어댑터 인터페이스는 목록과 재고 양쪽이 쓴다. consumer 쪽에 두면 반대편 consumer 가 그 패키지를 보게 되고,
  [ADR-0031](0031-supplier-adapter-boundaries.md) 이 정한 "두 경계를 묶는 인터페이스"는 두 consumer 패키지를 모두 보게 된다. `supplier` 에 모으면 그 일이 없다
- 매핑 테이블은 동기화가 쓰고 검색이 읽는 유일한 공유물이다. 저장소가 `catalog` 에 있으면 검색이 `catalog` 를 보게 된다.
  사라진 것을 표시하고([ADR-0037](0037-missing-catalog-entries-kept-and-marked.md)) 그 표시로 거르는 일이 한 패키지 안에 있어야 규칙이 갈라지지 않는다
- `config` 패키지는 만들지 않는다. 공급사 설정은 `supplier` 것이고, `stay.catalog.sync.*` 는 `@Scheduled` 의 문자열로 읽어 클래스가 없다

**규칙을 지키는 방법** — Kotlin 의 `internal` 은 모듈 단위라 컴파일러가 막지 못한다. 검토할 때 import 를 본다.

```
grep -rn "import com.stayaggregator.catalog" app/src/main/kotlin/com/stayaggregator/search
```

도구로 강제하는 것은 필요해질 때 [ADR-0006](0006-add-dependencies-when-needed.md) 에 따라 더한다.

## Consequences

**얻는 것**
- 새 공급사를 붙일 때 고칠 곳이 `supplier/<공급사>/` 디렉터리 하나와 설정 한 묶음으로 보인다. ADR-0031 이 말로 적은 것과 같다
- 검색이 목록 동기화 코드를 보지 않는다는 것이 import 로 확인된다
- 매핑 테이블의 SQL 이 한 패키지에만 있다

**잃는 것**
- 패키지가 하나 늘고, `mapping` 은 지금 파일이 넷뿐이다
- 의존 방향을 컴파일러가 막지 못해 검토에 기댄다
- `NormalizedHotel` 이 `mapping` 으로 가면서 "누가 정규화했나"가 이름만으로는 덜 보인다. KDoc 으로 적는다

**넘기는 것**
- 공급사별 `WebClient` 를 한 곳에서 만들지는 Q23(동시 호출 수·풀 설정)과 함께 정한다. 지금은 어댑터가 각자 만든다
- 공급사 클래스 이름은 재고·요금 경계를 함께 구현할 때 `SupplierAAdapter` 로 바꾼다. 지금 바꾸면 이름이 사실과 달라진다

## Discussion

- **AI 주장과 근거** — 기능별로 나누되 `supplier` / `catalog` / `search` / `config` 를 제안했다. 저장소는 목록 동기화가 쓰므로 `catalog` 에 두면 된다고 보았다
- **반박** — 독립 검토는 저장소를 `mapping` 으로 꺼내라고 했다. 검색이 같은 테이블을 읽으므로 `search → catalog` 의존이 생기고,
  어댑터 인터페이스를 consumer 쪽에 두면 묶는 인터페이스와 실패 신호가 양쪽을 다 보게 된다는 지적이었다
- **검증 결과** — 지적이 맞다. 인용된 Spring·Kotlin 문서를 원문과 대조했고, 문서가 기능별을 명시해 권하지는 않는다는 단서도 사실이었다.
  다만 검토가 든 사실 하나는 틀렸다. "`application.yml` 에 `stay.*` 가 없어 저장소만으로는 앱이 뜨지 않는다"고 했으나
  `app/src/main/resources/application.yml` 에 있고 실제로 기동된다. 인용한 파일 줄 번호도 실제와 달랐다
- **그래서 어떻게 바뀌었나** — `mapping` 을 더하고 `config` 를 뺐다. 어댑터 인터페이스와 공통 형태를 `supplier` 로 옮기기로 했다

## Verification

패키지가 정한 대로 나뉘어 있는지
  → `app/src/main/kotlin/com/stayaggregator` 아래에 `catalog`·`mapping`·`supplier` 셋. `search` 는 검색 구현 단위에서 생긴다

`supplier` 와 `mapping` 이 다른 패키지를 보지 않는지
  → `grep -rn "import com.stayaggregator." app/src/main/kotlin/com/stayaggregator/supplier app/src/main/kotlin/com/stayaggregator/mapping`
    를 돌리면 `mapping` 은 한 줄도 나오지 않고, `supplier` 는 하위 패키지(`a`·`b`)가 부모인 `supplier` 를 가리키는 줄만 나온다(2026-09-17 확인)

검색을 만든 뒤 `search` 가 `catalog` 를 import 하지 않는지는 그때 확인하고 여기에 적는다
  → `grep -rn "import com.stayaggregator.catalog" app/src/main/kotlin/com/stayaggregator/search`
