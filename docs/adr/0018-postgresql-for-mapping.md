---
id: 0018
title: 매핑을 저장할 DB 로 PostgreSQL 채택
status: accepted
date: 2026-09-15
---

# 0018. 매핑을 저장할 DB 로 PostgreSQL 채택

## Context

매핑은 서버형 관계형 DB 에 저장하기로 했다([ADR-0017](0017-mapping-in-server-rdb.md)). 요구사항이 예로 든 서버형
관계형 DB 는 PostgreSQL 과 MySQL 이다. 매핑에는 무작위 UUID 로 발급한 내부 식별자를 저장하고
([ADR-0011](0011-internal-id-random-uuid.md)), 숙소 목록을 다시 받아올 때 공급사와 공급사 코드로 기존 행을 찾아 갱신한다
([ADR-0013](0013-catalog-sync-on-startup-and-interval.md)). 선택 항목으로 같은 숙소 병합(이름으로 추정)과 정규화 실패 격리도 하기로 했다.

두 DB 에서 확인한 차이는 이렇다.

| 지점 | PostgreSQL | MySQL | 원문 |
|---|---|---|---|
| 스키마 변경 되돌리기 | 데이터베이스·테이블스페이스 추가·삭제를 빼면 테이블 생성·변경을 트랜잭션 안에서 롤백할 수 있다 | `CREATE TABLE`, `ALTER TABLE` 같은 문은 진행 중인 트랜잭션을 암묵적으로 커밋한다 | [PostgreSQL 위키](https://wiki.postgresql.org/wiki/Transactional_DDL_in_PostgreSQL:_A_Competitive_Analysis#Transactional_DDL:~:text=You%20can%27t%20recover%20from%20an%20add%2Fdrop%20on%20a%20database%20or%20tablespace) · [MySQL](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html#:~:text=implicitly%20end%20any%20transaction%20active%20in%20the%20current%20session) |
| 문자열 유사도 | `pg_trgm` 이 유사도 함수·연산자와 이를 받치는 인덱스를 제공 | n-gram 파서는 전문 검색용 토큰화로, 유사도 점수를 주지 않는다 | [pg_trgm](https://www.postgresql.org/docs/current/pgtrgm.html#PGTRGM:~:text=determining%20the%20similarity%20of%20alphanumeric%20text%20based%20on%20trigram%20matching) · [MySQL ngram](https://dev.mysql.com/doc/refman/8.4/en/fulltext-search-ngram.html#:~:text=tokenizes%20a%20sequence%20of%20text%20into%20a%20contiguous%20sequence%20of%20n%20characters) |
| JSON 검색 | `jsonb` 에 인덱스를 바로 걸어 키·값을 검색 | JSON 컬럼은 직접 인덱싱하지 않고 생성 컬럼이나 다중값 인덱스를 쓴다 | [jsonb 인덱스](https://www.postgresql.org/docs/current/datatype-json.html#JSON-INDEXING:~:text=efficiently%20search%20for%20keys%20or%20key%2Fvalue%20pairs) · [MySQL JSON](https://dev.mysql.com/doc/refman/8.4/en/json.html#:~:text=are%20not%20indexed%20directly) |
| UUID 저장 | `uuid` 전용 타입(128비트), v4·v7 생성 기본 지원 | 전용 타입 없음. `UUID()` 는 v1 | [PostgreSQL uuid](https://www.postgresql.org/docs/current/datatype-uuid.html#DATATYPE-UUID:~:text=native%20support%20for%20generating%20UUIDs%20using%20the%20UUIDv4%20and%20UUIDv7%20algorithms) · [MySQL UUID()](https://dev.mysql.com/doc/refman/8.4/en/miscellaneous-functions.html#function_uuid:~:text=conforms%20to%20UUID%20version%201%20as%20described%20in%20RFC%204122) |
| 있으면 갱신, 없으면 삽입 | `ON CONFLICT … DO UPDATE` | `ON DUPLICATE KEY UPDATE`. 유니크 인덱스가 여러 개인 테이블에서는 피하라고 안내 | [PostgreSQL](https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT:~:text=ON%20CONFLICT%20DO%20NOTHING%20simply%20avoids%20inserting%20a%20row) · [MySQL](https://dev.mysql.com/doc/refman/8.4/en/insert-on-duplicate.html#:~:text=avoid%20using%20an%20ON%20DUPLICATE%20KEY%20UPDATE%20clause%20on%20tables%20with%20multiple%20unique%20indexes) |
| 기본 격리 수준 | Read Committed | Repeatable Read | [PostgreSQL](https://www.postgresql.org/docs/current/transaction-iso.html#XACT-READ-COMMITTED:~:text=Read%20Committed%20is%20the%20default%20isolation%20level%20in%20PostgreSQL) · [MySQL](https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html#:~:text=The%20default%20isolation%20level%20for%20InnoDB%20is%20REPEATABLE%20READ) |

## Options

- **(가) PostgreSQL** ← AI 추천 — 스키마 변경이 실패해도 트랜잭션으로 되돌려 반쯤 적용된 상태가 남지 않는다.
  대신 최근에 더 많이 쓴 쪽이 아니다.
- **(나) MySQL** — 최근에 더 많이 써서 익숙하다. 대신 스키마 변경은 암묵적으로 커밋되어, 마이그레이션이 중간에
  실패하면 일부만 바뀐 상태를 직접 수습해야 한다.

## Decision

**(가)** 를 택한다. 매핑은 PostgreSQL 에 저장한다.

**이유** — 두 DB 모두 이 설계를 담는 데 차이가 작다. 확인된 차이 중 스키마 변경이 실패해도 트랜잭션으로 되돌려
반쯤 적용된 상태가 남지 않는다는 점을 고려해 PostgreSQL 을 택한다.

## Consequences

**얻는 것**
- 테이블이 늘고 마이그레이션이 반복돼도, 실패한 변경이 일부만 반영된 채 남지 않는다

**잃는 것**
- 최근에 더 많이 써서 익숙한 MySQL 대신 고른 것이라, 장애를 들여다볼 때 익숙함에 기대기 어렵다

**확인하지 않은 것**
- 행이 많은 테이블을 바꿀 때의 잠금 시간과 서비스 중단 없는 변경 방식은 두 DB 모두 확인하지 않았다

**아직 정하지 않은 것**
- 병합에서 이름 유사도를 어디서 계산할지. 사용자는 앱에서 계산할 방향을 밝혔고, 병합을 설계할 때 정한다
- 내부 식별자를 `uuid` 전용 타입으로 둘지 문자열로 둘지. 테이블을 설계할 때 정한다

## Discussion

- **AI 주장과 근거** — 처음에는 UUID 전용 타입과, 재동기화 때 어느 유니크 조건에 걸리면 갱신할지 지정하는 문법을
  PostgreSQL 의 이점으로 들었다
- **반박** — 재동기화 문법은 특장점으로 보기 어렵고 MySQL 로도 된다. UUID 전용 타입을 쓰면 식별자 형식을 바꿀 때
  컬럼 타입 마이그레이션이 필요하다. UUID 를 문자열로 두는 손해가 그렇게 큰가. 이것 외에 두 DB 를 가를 명분은 없나
- **검증 결과** — 재동기화 문법은 내부 식별자가 새로 발급한 값이라 다른 유니크 조건과 겹칠 일이 드물어 차별점이 아니었다.
  UUID 전용 타입은 형식 검증이라는 이점과 형식 변경 시 묶임이 함께 있어 비겼다. 선택 항목까지 넓혀 공식 문서를 다시
  확인해 스키마 변경 롤백, 문자열 유사도, JSON 인덱스, 기본 격리 수준의 차이를 찾았다. 사용자는 유사도는 앱에서 계산할
  것 같다며 차이가 작다고 보고, "대규모 데이터에서 더 안정적인 마이그레이션"을 이유로 들었다. 확인된 것은 실패한 변경의
  롤백까지였고, 대규모 테이블 변경 때의 잠금 동작은 확인하지 않아 그 표현은 뒷받침되지 않았다
- **그래서 어떻게 바뀌었나** — AI 가 처음 든 두 근거는 뺐다. 이유 문장을 확인된 범위인 "실패한 스키마 변경을 되돌릴 수
  있다"로 좁혀 적었다. 최근에 더 익숙한 쪽이 MySQL 이라는 사실은 이유가 아니라 잃는 것으로 옮겼다

## Verification

아직 구현 전이다. DB 실행 구성과 마이그레이션을 추가할 때 "PostgreSQL 로 기동해 매핑 테이블이 만들어지는지"
확인하는 테스트나 실행 명령을 이 절에 적는다.
