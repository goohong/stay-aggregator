---
id: 0032
title: 매핑 저장은 Spring JDBC 의 JdbcClient 로 하기로 결정
status: accepted
date: 2026-09-16
---

# 0032. 매핑 저장은 Spring JDBC 의 JdbcClient 로 하기로 결정

## Context

DB 에 저장하는 것은 공급사 코드와 내부 식별자의 매핑뿐이다([ADR-0017](0017-mapping-in-server-rdb.md), [ADR-0018](0018-postgresql-for-mapping.md)).
요금·재고는 저장하지 않는다. 매핑에 하는 일은 셋이다.

- 목록 동기화에서 (공급사, 공급사 코드) 기준으로 이미 있으면 두고 없으면 넣는다. 숙소와 객실 타입 두 단계다([ADR-0012](0012-static-info-from-catalog.md))
- 내부 식별자는 우리가 만든 무작위 UUID 라 DB 가 만들어 주지 않는다([ADR-0011](0011-internal-id-random-uuid.md))
- 검색에서 공급사별 코드 목록을 읽는다

연관 관계를 타고 다니는 조회도, 조회 후 값을 바꿔 두면 알아서 반영되는 흐름도 이 설계에는 없다.

확인한 사실

| 사실 | 원문 |
|---|---|
| Boot 4.1.1 의 JPA 스타터는 Hibernate 를 함께 들여온다(Hibernate 7.4.5) | [spring-boot-starter-data-jpa build.gradle](https://github.com/spring-projects/spring-boot/blob/v4.1.1/starter/spring-boot-starter-data-jpa/build.gradle): "Starter for using Spring Data JPA with Hibernate" · [의존성 목록](https://github.com/spring-projects/spring-boot/blob/v4.1.1/platform/spring-boot-dependencies/build.gradle) |
| Boot 4.1.1 의 JDBC 스타터는 HikariCP 와 함께 온다 | [spring-boot-starter-jdbc build.gradle](https://github.com/spring-projects/spring-boot/blob/v4.1.1/starter/spring-boot-starter-jdbc/build.gradle): "Starter for using JDBC with the HikariCP connection pool" |
| `JdbcClient` 는 Spring 6.1 부터 있는, 질의·갱신문을 위한 단순한 창구다 | [Spring Framework, JDBC core](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html#:~:text=JdbcClient%20is%20a%20flexible%20but%20simplified%20facade): "JdbcClient is a flexible but simplified facade for JDBC query/update statements." |
| Spring Data JDBC 는 지연 로딩·캐시·변경 감지가 없고, 매핑 방식이 단순한 경우를 위한 것이다 | [Spring Data JDBC, Why?](https://docs.spring.io/spring-data/relational/reference/jdbc/why.html): "No lazy loading or caching is done." / "There is no dirty tracking and no session." / "There is a simple model of how to map entities to tables. It probably only works for rather simple cases." |

## Options

- **(가) Spring Data JPA** — 엔티티와 리포지토리로 기본 CRUD 가 짧아지고, 이 프로젝트에서 가장 익숙한 방식이다.
  대신 영속성 컨텍스트·식별자 할당·flush 시점 같은 개념이 따라온다. 식별자를 우리가 만들므로 새 행인지 기존 행인지 판단을 따로 알려 줘야 하고,
  여러 건을 한 번에 넣으면서 있으면 건너뛰는 처리는 결국 직접 쿼리로 쓰게 된다.
- **(나) Spring JDBC 의 `JdbcClient`** ← AI 추천 — SQL 이 그대로 보이고, "있으면 두고 없으면 넣기"를 한 문장으로 쓴다. 설명할 개념이 적다.
  대신 조회 결과를 객체로 바꾸는 코드를 직접 쓴다.
- **(다) Spring Data JDBC** — 리포지토리 형태를 유지하면서 ORM 은 없다. 대신 집합체 단위로 저장하는 규칙을 따로 이해해야 하고,
  식별자를 우리가 만들 때 새 행 판단 문제는 (가)와 비슷하게 남는다.

## Decision

**(나)** 를 택한다. 매핑 저장과 조회는 `spring-boot-starter-jdbc` 의 `JdbcClient` 로 한다.
의존성은 목록 동기화를 구현할 때 추가한다([ADR-0006](0006-add-dependencies-when-needed.md)).

**이유** — 이 설계가 DB 에 하는 일은 매핑 읽기와 쓰기뿐이고, ORM 이 주는 지연 로딩·변경 감지·연관 관계 탐색을 쓸 곳이 없다.
반대로 자주 쓸 "여러 건을 한 번에, 있으면 두고 없으면 넣기"는 SQL 로 쓰는 편이 짧고 의도가 드러난다.

## Consequences

**얻는 것**
- 저장 계층에서 설명할 개념이 SQL 과 매핑 코드뿐이다
- 여러 건 upsert 를 DB 문법 그대로 쓴다

**잃는 것**
- 조회 결과를 객체로 바꾸는 코드와 SQL 을 직접 쓴다. 테이블이 늘면 그 양도 는다
- 이 프로젝트에서 더 익숙한 쪽은 JPA 였다. 병합 그룹(ADR-0010)처럼 테이블이 늘면 JPA 의 이점이 커질 수 있다

## Verification

JPA 스타터가 없고 JDBC 스타터가 있는지
  → `app/build.gradle.kts` 에 `spring-boot-starter-jdbc` 만 있고 `spring-boot-starter-data-jpa` 는 없다

매핑 저장 코드가 `JdbcClient` 를 쓰는지
  → `app/src/main/kotlin/com/stayaggregator/mapping/MappingRepository.kt` 의 생성자

저장이 실제 PostgreSQL 에서 도는지
  → `CatalogSyncIntegrationTest` 의 열네 건

## Discussion

해당 없음. 추천과 결정이 같았다.
