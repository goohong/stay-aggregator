---
id: 0033
title: 스키마 변경 도구로 Flyway 채택
status: accepted
date: 2026-09-16
---

# 0033. 스키마 변경 도구로 Flyway 채택

## Context

매핑은 PostgreSQL 에 저장하고([ADR-0018](0018-postgresql-for-mapping.md)) 저장·조회는 `JdbcClient` 로 한다([ADR-0032](0032-mapping-persistence-with-jdbcclient.md)).
테이블을 만들고 이후 바꾸는 일을 무엇으로 할지 정해야 한다. 지금 테이블은 매핑 두 종류뿐이지만, 병합 그룹([ADR-0010](0010-keep-internal-id-on-merge.md))이나
어긋남 기록(Q17) 같은 것이 붙으면 스키마가 늘어난다.

확인한 사실 (모두 [Spring Boot, Database Initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html) 과 Boot 4.1.1 의 [의존성 목록](https://github.com/spring-projects/spring-boot/blob/v4.1.1/platform/spring-boot-dependencies/build.gradle))

| 사실 | 원문 |
|---|---|
| Flyway 는 스타터를 넣으면 기동 시 `classpath:db/migration` 의 스크립트를 실행한다. 이름은 `V<버전>__<이름>.sql` | "To automatically run Flyway database migrations on startup, add the `spring-boot-starter-flyway` starter to your classpath." |
| Liquibase 는 기본으로 `db/changelog/db.changelog-master.yaml` 을 읽고 YAML·JSON·XML·SQL 형식을 지원한다 | "add the `spring-boot-starter-liquibase` starter to your classpath" |
| 기본 SQL 스크립트(`schema.sql`)를 마이그레이션 도구와 함께 쓰는 것은 권장되지 않는다 | "Using the basic `schema.sql` and `data.sql` scripts alongside Flyway or Liquibase is not recommended and support will be removed in a future release." |
| Boot 4.1.1 이 관리하는 버전은 Flyway 12.4.0, Liquibase 5.0.3 | 의존성 목록 |
| Flyway 의 되돌리기(undo)는 유료 판의 기능이다 | [Flyway, Undo migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/undo-migrations): "An undo migration is responsible for undoing the effects of the versioned migration with the same version." (Teams edition) |
| Liquibase 의 되돌리기 명령(`rollback`, `rollback-count` 등)은 무료 판에서도 쓸 수 있다. 다만 SQL 형식 변경 로그에는 직접 쓴 되돌리기 정의를 넣을 수 없다 | [Liquibase, Rollback commands](https://docs.liquibase.com/commands/rollback/home.html): "Custom rollback definitions are not supported in formatted SQL changelogs." |

## Options

- **(가) Flyway** ← AI 추천 — 변경이 SQL 파일로 남고 PostgreSQL 문법을 그대로 쓴다. 배울 것이 파일 이름 규칙 정도다.
  대신 되돌리기 기능은 유료 판이라, 잘못 나간 변경은 되돌리는 변경을 새로 써야 한다.
- **(나) Liquibase** — DB 에 중립적인 형식으로 변경을 적을 수 있고 되돌리기 명령이 무료 판에 있다.
  대신 중립 형식의 이점은 DB 를 바꿀 때 나오는데 우리는 PostgreSQL 로 고정했고, 변경 로그 형식과 개념을 더 배워야 한다.
- **(다) 도구 없이 `schema.sql`** — 파일 하나면 된다. 대신 무엇이 언제 적용됐는지 이력이 남지 않아, 테이블을 바꿀 때 이미 적용된 변경을 다시 돌리지 않도록 직접 신경 써야 한다.

## Decision

**(가)** 를 택한다. 스키마는 `classpath:db/migration` 의 Flyway 스크립트로 만들고 바꾼다. 기본 `schema.sql` 은 쓰지 않는다.
의존성은 매핑 테이블을 만들 때 추가한다([ADR-0006](0006-add-dependencies-when-needed.md)).

**이유** — 스키마는 앞으로 늘어나고, 무엇을 언제 바꿨는지가 파일로 남아야 한다. 우리는 DB 를 PostgreSQL 로 고정했으므로 DB 중립 형식의 이점이 작고,
SQL 을 그대로 쓰는 쪽이 `JdbcClient` 선택과 같은 결이다.

## Consequences

**얻는 것**
- 스키마 변경 이력이 SQL 파일로 남고, 기동할 때 적용된다
- ADR-0018 에서 PostgreSQL 을 고른 이유(실패한 스키마 변경이 트랜잭션으로 되돌아감)를 실제로 쓰는 자리가 생긴다

**잃는 것**
- 도구가 하나 늘고, 테스트에서 스키마를 준비하는 방식도 여기에 맞춰야 한다
- 되돌리기 기능은 유료 판이라, 잘못 나간 변경은 되돌리는 변경을 새로 쓴다
- 테이블이 두어 개뿐인 지금은 `schema.sql` 로도 됐다

## Verification

`app/build.gradle.kts` 에 Flyway 스타터가 있고, `app/src/main/resources/db/migration` 에 스크립트가 있는지. 앱을 띄웠을 때 매핑 테이블이 만들어지는지.

## Discussion

해당 없음. 추천과 결정이 같았다.
