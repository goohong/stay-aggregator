---
id: 0034
title: 내부 식별자를 uuid 컬럼 타입으로 저장하기로 결정
status: accepted
date: 2026-09-16
---

# 0034. 내부 식별자를 uuid 컬럼 타입으로 저장하기로 결정

## Context

내부 식별자는 앱이 만드는 무작위 UUID 이고 한 번 발급하면 바뀌지 않는다([ADR-0010](0010-keep-internal-id-on-merge.md), [ADR-0011](0011-internal-id-random-uuid.md)).
[ADR-0018](0018-postgresql-for-mapping.md) 은 이 값을 `uuid` 전용 타입으로 둘지 문자열로 둘지를 "테이블을 설계할 때 정한다"며 미뤄 두었다.
저장·조회는 `JdbcClient` 로 하고([ADR-0032](0032-mapping-persistence-with-jdbcclient.md)) 스키마 변경은 Flyway 로 한다([ADR-0033](0033-flyway-for-schema-migration.md)).

확인한 사실 (PostgreSQL 공식 문서 원문 대조)

| 사실 | 원문 |
|---|---|
| uuid 는 128비트 값이다 | [datatype-uuid](https://www.postgresql.org/docs/current/datatype-uuid.html#:~:text=This%20identifier%20is%20a%20128%2Dbit%20quantity): "This identifier is a 128-bit quantity" |
| 대문자·중괄호 같은 다른 표기를 입력으로 받고, 출력은 항상 표준형이다 | 같은 페이지: "use of upper-case digits, the standard format surrounded by braces, omitting some or all hyphens" / "Output is always in the standard form." |
| 문자열 타입은 값에 1바이트를 더해 저장하고, 문자열 타입끼리 성능 차이는 없다 | [datatype-character](https://www.postgresql.org/docs/current/datatype-character.html#:~:text=1%20byte%20plus%20the%20actual%20string): "1 byte plus the actual string" / "There is no performance difference among these three types" |
| 컬럼 타입을 바꾸면 테이블과 인덱스를 다시 쓰고, `ACCESS EXCLUSIVE` 잠금이 걸리며, 디스크가 한때 두 배까지 필요할 수 있다 | [sql-altertable](https://www.postgresql.org/docs/current/sql-altertable.html#:~:text=entire%20table%20and%20its%20indexes%20to%20be%20rewritten): "cause the entire table and its indexes to be rewritten" / "An ACCESS EXCLUSIVE lock is acquired unless explicitly noted" / "temporarily require as much as double the disk space" |

별도 검토가 PostgreSQL 17.11 컨테이너에서 측정한 값(우리가 재현하지는 않았다): 10만 행 기준 기본 키 인덱스가 `uuid` 4.3MB / `text` 7.8MB,
타입 변경은 왕복 0.4초 미만. 문자열 컬럼은 같은 UUID 를 대문자와 소문자로 각각 다른 행으로 받아들였고, `uuid` 컬럼은 하나로 정규화했다.

## Options

- **(가) `uuid` 타입** ← AI 추천 — 16바이트로 저장돼 인덱스가 작고, 표기가 달라도 한 값으로 정규화되며, UUID 가 아닌 값은 DB 가 거부한다.
  대신 식별자 형식을 UUID 가 아닌 것으로 바꾸면 컬럼 타입 변경 마이그레이션이 필요하다.
- **(나) 문자열(`text`)** — 형식을 바꿔도 컬럼은 그대로다. 대신 표기가 섞여 들어오면 같은 식별자가 다른 행이 되고, 그것을 앱 코드와 테스트로 계속 막아야 한다. 인덱스도 커진다.

## Decision

**(가)** 를 택한다. 내부 식별자 컬럼은 PostgreSQL 의 `uuid` 타입으로 둔다.

**이유** — 컬럼 타입은 업무 규칙이 아니라 값의 종류를 정하는 것이다. 이 값은 설계상 128비트 무작위 값 그 자체이고(ADR-0011),
표기 차이로 같은 식별자가 다른 행이 되는 일을 DB 가 막아 준다. 형식을 UUID 가 아닌 것으로 바꾸는 순간은 ADR-0011 을 뒤집는 순간이라,
그때는 발급 코드와 API 계약이 함께 바뀌고 컬럼 타입 변경은 그 일부다.

## Consequences

**얻는 것**
- 표기가 다른 같은 UUID 가 다른 행으로 들어가지 않는다
- 인덱스가 문자열보다 작다
- UUID 가 아닌 값이 들어오면 DB 가 거부한다

**잃는 것**
- 식별자 형식을 바꾸면 테이블과 인덱스를 다시 쓰는 마이그레이션이 필요하고 그동안 잠금이 걸린다. 10만 행에서 1초 미만으로 관측됐다(위 측정, PostgreSQL 17.11 기준. 저장소가 쓰는 18 에서는 재현하지 않았다)
- 파라미터를 문자열로 넘기면 타입이 맞지 않아 실패한다. 앱에서 식별자를 `UUID` 값으로 들고 그대로 넘겨야 한다

**넘기는 것**
- 기본 키를 무엇으로 둘지와 유일성 제약을 어디에 둘지는 테이블을 설계할 때 정한다

## Discussion

- **AI 주장과 근거** — `uuid` 를 권하며 인덱스 크기와 형식 검사를 이유로 들었다
- **반박** — 실무에서는 DB 를 바꾸기 어려운 자원으로 보고 검증을 최소화해 문자열로 두기도 한다는 지적을 받았다
- **검증 결과** — 그 관행이 있다는 전제를 받아들이더라도 적용 대상이 다르다(관행의 실재 여부는 확인하지 않았다). 값 범위를 강제하는 CHECK 제약, DB 의 열거 타입, 외래 키처럼 **업무 규칙이 스키마에 박히는 것**에는 잘 맞는다.
  컬럼 타입은 값의 종류를 정하는 것이고, `uuid` 가 거부하는 것은 "UUID 가 아닌 값" 하나뿐이다. 또 바꾸기 어려운 것은 이미 발급돼 밖에서 쓰이는 식별자 **값**이지 컬럼 타입이 아니며, 값은 어느 타입이든 똑같이 못 바꾼다.
  방향도 비대칭이었다. `uuid` 에서 문자열로 가는 것은 변환 한 줄이지만, 문자열로 두다가 `uuid` 로 가려면 그 사이 들어간 표기가 섞인 값을 먼저 정리해야 한다
- **그래서 어떻게 바뀌었나** — 결정은 그대로 두고, 잃는 것에 마이그레이션 비용과 파라미터 바인딩 주의를 적었다

## Verification

Flyway 스크립트에서 내부 식별자 컬럼이 `uuid` 타입인지. 매핑 저장 코드가 식별자를 `UUID` 값으로 넘기는지.
