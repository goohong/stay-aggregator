---
id: 0036
title: 매핑 테이블을 두 개로 두고 내부 식별자를 키로 쓰기로 결정
status: accepted
date: 2026-09-16
---

# 0036. 매핑 테이블을 두 개로 두고 내부 식별자를 키로 쓰기로 결정

## Context

요구사항은 두 단계 매핑을 요구한다. (공급사, 숙소 코드) → 내부 숙소 식별자, (공급사, 숙소 코드, 객실 타입 코드) → 내부 객실 타입 식별자다.
검색 응답에는 두 식별자가 모두 들어가고, 숙소명·객실 타입명·최대 수용 인원은 저장된 목록 값을 쓴다([ADR-0012](0012-static-info-from-catalog.md)).
객실 타입 코드는 그 숙소 안에서만 유일하다.

[ADR-0011](0011-internal-id-random-uuid.md) 은 내부 식별자를 무작위 UUID 로 발급하기로 했지만, 문장이 `(공급사, 공급사 코드)` 로 적혀 있어 숙소만 다뤘다.
[ADR-0034](0034-internal-id-as-uuid-column.md) 는 그 식별자를 `uuid` 컬럼에 저장하기로 했다. 저장은 `JdbcClient`, 스키마는 Flyway 다([ADR-0032](0032-mapping-persistence-with-jdbcclient.md), [ADR-0033](0033-flyway-for-schema-migration.md)).

확인한 사실

| 사실 | 원문 |
|---|---|
| InnoDB 는 기본 키를 클러스터 인덱스로 쓰고, 보조 인덱스마다 기본 키 값을 함께 담는다. 그래서 긴 기본 키는 공간을 더 쓴다 | [MySQL, Clustered and Secondary Indexes](https://dev.mysql.com/doc/refman/8.4/en/innodb-index-types.html): "When you define a `PRIMARY KEY` on a table, `InnoDB` uses it as the clustered index." / "If the primary key is long, the secondary indexes use more space, so it is advantageous to have a short primary key." |
| PostgreSQL 은 테이블을 인덱스 순서로 저장하지 않는다. `CLUSTER` 는 한 번의 정렬이고 이후 행은 그 순서로 쌓이지 않는다 | [PostgreSQL, CLUSTER](https://www.postgresql.org/docs/current/sql-cluster.html): "Clustering is a one-time operation: when the table is subsequently updated, the changes are not clustered." / "no attempt is made to store new or updated rows according to their index order" |
| PostgreSQL 은 무작위 UUID(`uuidv4`)와 시간 순서 UUID(`uuidv7`) 생성 함수를 모두 제공한다 | [PostgreSQL, UUID Functions](https://www.postgresql.org/docs/current/functions-uuid.html): "Generates a version 7 (time-ordered) UUID" |

## Options

객실 타입 식별자의 형태
- **(가) 숙소와 같은 무작위 UUID** ← 최종 — 발급·저장·조회 코드가 한 가지다. 대신 값만 보고 숙소인지 객실 타입인지 구분되지 않는다.
- **(나) 순번** — ADR-0011 이 숙소 식별자에서 버린 이유(개수·순서 노출)가 그대로 적용된다.
- **(다) 내부 숙소 식별자에 번호를 이어 붙인 값** — 값 하나에 숙소가 드러나고, 받는 쪽이 파싱해 쓸 여지가 생긴다.

객실 타입 행이 숙소를 가리키는 방법
- **(가) 내부 숙소 식별자로 가리킨다** ← 최종 — 공급사 숙소 코드가 숙소 테이블에만 있고, 숙소 없는 객실 타입 행을 DB 가 막는다. 대신 공급사 코드로 시작하는 조회가 두 단계가 된다.
- **(나) 공급사와 숙소 코드를 그대로 들고 있다** — 공급사가 준 세 값으로 한 번에 찾는다. 대신 같은 코드가 두 테이블에 적히고 어긋나지 않게 할 책임이 코드로 넘어온다.

키 구성
- **(A) 내부 식별자(`uuid`)를 그대로 기본 키로 쓴다** ← 최종 — 컬럼과 인덱스가 하나다. 대신 무작위 값이라 인덱스에 흩어져 쌓인다.
- **(B) 숫자 기본 키를 따로 두고 `uuid` 는 유니크 컬럼으로 둔다** — 기본 키가 순서대로 쌓이고 참조 컬럼이 8바이트다. 대신 식별자를 둘 들고 다녀야 하고, 외부 식별자로 찾을 때 한 단계가 늘어난다.

## Decision

매핑은 테이블 두 개로 둔다.

| 테이블 | 컬럼 | 유일성 |
|---|---|---|
| 숙소 매핑 | 내부 숙소 식별자(`uuid`, 기본 키), 공급사, 공급사 숙소 코드, 숙소명 | (공급사, 공급사 숙소 코드) |
| 객실 타입 매핑 | 내부 객실 타입 식별자(`uuid`, 기본 키), 내부 숙소 식별자(숙소 매핑 참조), 객실 타입 코드, 객실 타입명, 최대 수용 인원 | (내부 숙소 식별자, 객실 타입 코드) |

- 내부 숙소 식별자와 내부 객실 타입 식별자는 **둘 다** 앱에서 발급하는 무작위 UUID 다. ADR-0011 의 결정이 객실 타입에도 적용된다는 것을 여기서 명시한다
- 객실 타입 행은 공급사 숙소 코드를 따로 두지 않고 내부 숙소 식별자로 숙소를 가리킨다
- 내부 식별자를 그대로 기본 키로 쓴다. 숫자 기본 키를 따로 두지 않는다

**이유** — 응답에 내보내는 값이 내부 식별자이고 조회도 그 값으로 들어오므로, 키를 하나로 두면 코드가 식별자 하나만 다룬다.
쓰기는 목록 동기화 때만 일어나고(기본 하루 한 번, [ADR-0013](0013-catalog-sync-on-startup-and-interval.md)·[ADR-0016](0016-catalog-sync-interval-default-daily.md)) 요청 경로에는 없어서, 무작위 키가 인덱스에 흩어지는 비용이 드러날 자리가 좁다.

## Consequences

**얻는 것**
- 공급사 숙소 코드가 한 곳에만 있고, 숙소 매핑에 없는 객실 타입 행이 생기지 않는다
- 코드가 식별자를 하나만 다룬다

**잃는 것**
- 공급사가 준 코드로 시작하는 조회가 숙소 → 객실 타입 두 단계가 된다. 검색 경로에서 이 조회를 어떻게 할지(한 번에 읽어 두기 등)는 검색을 구현할 때 본다
- 무작위 UUID 라 인덱스에 흩어져 쌓인다. 쓰기가 늘어 문제가 되면 먼저 시간 순서 UUID(v7)로 바꿔 볼 수 있고(컬럼 타입은 그대로), 그래도 부족하면 숫자 기본 키를 더하는 마이그레이션이 필요하다
- 참조 컬럼이 8바이트가 아니라 16바이트다

## Discussion

- **AI 주장과 근거** — 매핑 테이블 예시를 들면서 객실 타입 내부 식별자를 UUID 로 그려 보였다
- **반박** — 정한 적 없는데 왜 UUID 인가. 그리고 무작위 UUID 는 값이 중간에 끼어들어 쓰기 비용이 크고 최적화를 덜 받는다고 들었는데, 숫자 키를 안에 두고 UUID 를 밖에 쓰는 방식은 고려하지 않아도 되나
- **검증 결과**
  - 객실 타입 식별자는 실제로 미정이었다. ADR-0011 은 숙소 키 모양으로 적힌 문장이었고, 정해지지 않은 것을 정해진 것처럼 보이게 한 잘못이었다
  - 무작위 키의 부담은 DB 구조에 따라 무게가 다르다. InnoDB 는 기본 키가 곧 행의 저장 순서이고 보조 인덱스마다 기본 키 값을 담아 긴 무작위 키의 대가가 크지만,
    PostgreSQL 은 행을 인덱스 순서로 저장하지 않아 그 대가가 인덱스 쪽에 한정된다
  - PostgreSQL 공식 문서에는 무작위 UUID 기본 키가 느리다는 서술이 없었다. 우리가 측정하지도 않았다
  - 참고한 이전 프로젝트는 MySQL 에서 숫자 기본 키와 문자열 식별자 컬럼을 함께 쓰는 방식이었다. DB 와 접근 경로가 다르다
- **그래서 어떻게 바뀌었나** — 객실 타입 식별자를 선택지로 다시 내어 합의했고, 키 구성은 (A)로 정하되 부담이 드러나면 UUIDv7 → 숫자 키 순으로 손볼 수 있다고 기록했다

## Verification

두 테이블과 유일성 제약 둘이 있는지
  → `app/src/main/resources/db/migration/V1__mapping.sql` 의 `uk_hotel_mapping_supplier_code UNIQUE (supplier, supplier_hotel_code)` 와
    `uk_room_type_mapping_hotel_code UNIQUE (internal_hotel_id, room_type_code)`

객실 타입 테이블이 숙소 테이블을 참조하는지
  → 같은 파일의 `internal_hotel_id uuid NOT NULL REFERENCES hotel_mapping (internal_hotel_id)`

그 제약이 실제로 같은 코드를 하나로 모으는지
  → `CatalogSyncIntegrationTest.다시 동기화해도 내부 식별자가 그대로다`,
    `CatalogSyncIntegrationTest.숙소가 빠지면 그 숙소의 객실 타입도 표시되고 다시 나타나면 같은 식별자로 missing_since 가 비워진다`
