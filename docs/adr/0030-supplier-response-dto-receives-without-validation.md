---
id: 0030
title: 공급사 응답 DTO 는 스펙 필드를 모두 받기만 하고 판정은 내부 로직에 두기로 결정
status: accepted
date: 2026-09-15
---

# 0030. 공급사 응답 DTO 는 스펙 필드를 모두 받기만 하고 판정은 내부 로직에 두기로 결정

## Context

공급사 응답은 우리가 정한 스펙이 아니라 외부 API 의 응답이다. 스펙과 다른 응답을 어떻게 판정할지는
[ADR-0027](0027-spec-violation-handling-criteria.md) 이 정했지만, 그 판정을 응답 DTO 에서 할지 내부 로직에서 할지는 정하지 않았다.
어긋남 감지([ADR-0014](0014-detect-drift-in-search-correct-in-sync.md))는 조회 응답의 이름·최대 수용 인원을 쓴다.

확인한 사실

| 사실 | 원문 |
|---|---|
| Spring Boot 4.1.1 이 관리하는 Jackson 은 3.1.5 다 | [Boot 4.1.1 gradle.properties](https://github.com/spring-projects/spring-boot/blob/v4.1.1/gradle.properties#L15) |
| Jackson 3 는 모르는 필드를 기본으로 무시한다(2.x 는 실패) | [DeserializationFeature.java L148–L150](https://github.com/FasterXML/jackson-databind/blob/jackson-databind-3.1.5/src/main/java/tools/jackson/databind/DeserializationFeature.java#L148-L150): "Feature is disabled by default as of Jackson 3.0 (in 2.x it was enabled)." |
| Boot 는 Jackson 2 기본값을 쓰도록 설정했을 때만 이 기능을 따로 끈다 | [JacksonAutoConfiguration.java L515–L519](https://github.com/spring-projects/spring-boot/blob/v4.1.1/module/spring-boot-jackson/src/main/java/org/springframework/boot/jackson/autoconfigure/JacksonAutoConfiguration.java#L515-L519) |
| Jackson 3 는 필드 이름의 대소문자를 기본으로 구분한다 | [MapperFeature.java L400](https://github.com/FasterXML/jackson-databind/blob/jackson-databind-3.1.5/src/main/java/tools/jackson/databind/MapperFeature.java#L400) |
| Jackson 3 도 애너테이션은 `com.fasterxml.jackson.annotation` 패키지(jackson-annotations 2.21)를 쓰고, `@JsonIgnoreProperties(ignoreUnknown = true)` 로 클래스 단위 설정을 할 수 있다 | [jackson-bom 3.1.5 pom L60](https://github.com/FasterXML/jackson-bom/blob/jackson-bom-3.1.5/pom.xml#L60) · [JsonIgnoreProperties.java L54](https://github.com/FasterXML/jackson-annotations/blob/2.21/src/main/java/com/fasterxml/jackson/annotation/JsonIgnoreProperties.java#L54) |

공급사 A 의 스펙에는 숙소 코드 필드가 설명 표에서 `HotelCode`, 두 응답 예시에서 `hotelCode` 로 적혀 있다. 표의 나머지 필드와 공급사 B 스펙의 필드는 모두 소문자로 시작한다.

## Options

필드 범위
- **스펙의 모든 필드를 받는다** — 선택지를 따로 두지 않았다. 어긋남 감지처럼 출력에 직접 쓰지 않는 필드도 쓰임이 있고, 받을지 말지를 필드마다 판단하지 않는다.

모르는 필드
- **(가) 무시하되 공급사 응답 DTO 마다 `@JsonIgnoreProperties(ignoreUnknown = true)` 와 이유 주석으로 명시한다** ← AI 추천 — 범위가 공급사 응답에 한정된다. 대신 새 DTO 마다 붙여야 한다.
- **(나) 무시하되 `spring.jackson.deserialization.fail-on-unknown-properties: false` 전역 설정으로 명시한다** — 한 곳이다. 대신 우리가 받는 요청 본문까지 관대해진다.
- **(다) 실패로 본다** — 공급사가 필드를 추가만 해도 그 공급사 결과 전체가 부분 실패가 된다.

빠진 필드·null 의 판정 위치
- **(가) DTO 를 스펙대로 null 불가로 둔다** — 항목 하나의 필드만 빠져도 응답 전체의 변환이 실패한다.
- **(나) 응답 틀(`items`, `resultCode`, `data`)은 DTO 에서 엄격하게, 항목 필드는 받은 뒤 검사한다** — AI 가 처음 권했다. 틀의 판정이 역직렬화 설정에 묶인다.
- **(다) DTO 는 검증하지 않고 받기만 하며, 틀까지 포함한 판정을 내부 로직에서 한다** ← 최종 — 판정 규칙이 한곳에 모인다. 대신 DTO 필드가 null 허용 타입이 되어 스펙상 필수 여부가 타입으로 드러나지 않는다.

필드 이름 표기
- **(가) 응답 예시대로 `hotelCode` 만 받는다** ← 최종 — 실제로 `HotelCode` 로 오면 A 의 항목이 모두 제외되고 격리 기록에 남는다.
- **(나) `@JsonAlias("HotelCode")` 로 두 표기를 모두 받는다** — AI 가 처음 권했다. 표기가 다른 이유를 따지지 않고 열어 둔다.

## Decision

- 공급사 응답 DTO 는 스펙에 있는 필드를 모두 받는다
- 스펙에 없는 필드는 무시한다. Jackson 3 의 기본 동작이지만 기본값에 기대지 않고, 공급사 응답 DTO 마다 `@JsonIgnoreProperties(ignoreUnknown = true)` 와 이유 주석으로 명시한다
- DTO 는 검증하지 않는다. 필드는 null 을 허용해 받고, 스펙상 필수인 필드는 주석으로 적는다. 응답 틀을 포함해 빠진 필드·null 의 판정은 DTO 를 받은 뒤 내부 로직에서 ADR-0027 의 세 질문으로 한다
- 역직렬화 단계에서 실패하는 것은 JSON 문법 오류와 타입 불일치뿐이고, 이는 ADR-0027 의 첫 질문에서 공급사 실패로 본다
- 필드 이름은 응답 예시의 표기(`hotelCode`)대로 받는다

**이유** — 외부 응답은 우리 스펙이 아니므로, DTO 에서 잘라 내면 공급사 장애를 일부라도 허용하려 할 때 손쓸 자리가 없다. 판단이 들어간 규칙은 받는 틀인 DTO 의 역할이 아니고,
내부 로직에 모아야 역직렬화 라이브러리의 동작에 기대지 않고 유지보수할 수 있다. 기본 동작도 명시해 두면 라이브러리의 기본값이 바뀌거나 다른 사람이 읽을 때 의도가 남는다.
필드 이름은 스펙의 응답 예시와 표의 나머지 필드가 모두 소문자로 시작하므로 설명 표의 대문자 표기를 따로 받지 않는다.

## Consequences

**얻는 것**
- 공급사 응답의 판정 규칙이 내부 로직 한곳에 모이고, ADR-0027 의 세 질문을 그대로 코드로 옮길 수 있다
- 모르는 필드를 참는 범위가 공급사 응답에 한정된다

**잃는 것**
- DTO 필드가 null 허용이라 내부 로직에서 null 을 다뤄야 하고, 필수 여부는 주석으로만 드러난다
- 새 공급사 DTO 에 애너테이션을 잊으면 의도 표시가 빠진다. 동작은 Jackson 3 기본값이라 같다
- 공급사 A 가 실제로 `HotelCode` 로 보내면 A 의 결과가 모두 제외된다. 격리 기록으로 드러난다

## Discussion

- **AI 주장과 근거** — 모르는 필드는 Jackson 3 기본값이라 설정 없이도 무시된다고 설명했다. 빠진 필드는 응답 틀을 DTO 에서 엄격하게 두자고 권했고, 숙소 코드 표기 불일치에는 별칭으로 두 표기를 모두 받자고 권했다
- **반박**
  - 기본 동작이라도 명시하고 주석을 남겨야 라이브러리가 바뀌거나 다른 사람이 볼 때 의도를 알 수 있다
  - 외부 API 응답을 DTO 에서 잘라 내면 공급사 장애를 일부 허용할 수 없고, 판단이 들어간 로직은 DTO 의 역할이 아니다
  - 설명 표라서 대문자로 쓴 것 아닌가. 대문자를 굳이 허용해야 하나
- **검증 결과** — 명시 방법은 DTO 애너테이션과 전역 설정 두 가지였고, 전역 설정은 우리가 받는 요청까지 관대해졌다. DTO 에서 틀을 엄격하게 두면 ADR-0027 의 판정 일부가 역직렬화 설정으로 흩어졌다.
  표기 불일치는 표의 나머지 필드와 응답 예시가 모두 소문자로 시작해 표기 실수로 보는 편이 근거가 있었고, 별칭은 근거 없는 대비였다
- **그래서 어떻게 바뀌었나** — 모르는 필드는 DTO 에 명시, 판정은 틀까지 내부 로직으로, 필드 이름은 응답 예시대로 정했다

## Verification

공급사 응답 DTO 에 `@JsonIgnoreProperties(ignoreUnknown = true)` 와 주석이 있고, 스펙에 없는 필드·필드가 빠진 항목·틀이 빠진 응답을 넣은 역직렬화·정규화 테스트가 각각 무시·항목 제외·공급사 실패로 처리되는지. 테스트를 만들면 그 이름을 여기에 적는다.
