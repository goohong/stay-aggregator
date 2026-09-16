---
id: 0035
title: 로컬은 Docker Compose, 테스트는 Testcontainers 로 PostgreSQL 을 띄우기로 결정
status: accepted
date: 2026-09-16
---

# 0035. 로컬은 Docker Compose, 테스트는 Testcontainers 로 PostgreSQL 을 띄우기로 결정

## Context

매핑은 PostgreSQL 에 저장하고([ADR-0018](0018-postgresql-for-mapping.md)), 저장·조회는 `JdbcClient` 로 SQL 을 직접 쓰며([ADR-0032](0032-mapping-persistence-with-jdbcclient.md)),
스키마는 Flyway 스크립트로 만든다([ADR-0033](0033-flyway-for-schema-migration.md)). 이 DB 를 로컬에서 어떻게 띄우고 테스트에서 무엇을 쓸지 정해야 한다.

확인한 사실

| 사실 | 원문 |
|---|---|
| `spring-boot-docker-compose` 를 넣으면 기동할 때 compose 파일을 찾아 `docker compose up` 을 부르고, 접속 정보 빈을 만들며, 종료할 때 `docker compose stop` 을 부른다 | [Spring Boot, Development-time Services](https://docs.spring.io/spring-boot/reference/features/dev-services.html): "Search for a `compose.yml` and other common compose filenames in your working directory" / "Create service connection beans for each supported container" |
| 이미 떠 있으면 다시 띄우지 않고, 종료할 때 내리지도 않는다 | 같은 문서 |
| 테스트에서는 컨테이너 빈의 수명을 Boot 가 관리한다 | 같은 문서: "Containers will be started and stopped automatically." |
| Boot 4.1.1 이 관리하는 Testcontainers 는 2.0.5 | [의존성 목록](https://github.com/spring-projects/spring-boot/blob/v4.1.1/platform/spring-boot-dependencies/build.gradle) |
| H2 의 다른 DB 흉내는 일부만 구현돼 있다 | [H2, Compatibility Modes](https://www.h2database.com/html/features.html#compatibility): "only a small subset of the differences between databases are implemented in this way" |
| 컨테이너를 테스트 메서드마다 새로 띄울지, 공유할지는 선언 방식이 정한다 | [Testcontainers, JUnit 5](https://java.testcontainers.org/test_framework_integration/junit_5/): "Containers declared as instance fields will be started and stopped for every test method." / "Containers declared as static fields will be shared between test methods." |
| 스프링은 같은 설정의 테스트끼리 애플리케이션 컨텍스트를 캐시해 재사용한다 | [Spring Framework, Context Caching](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html): "that context is cached and reused for all subsequent tests that declare the same unique context configuration" |

## Options

- **(가) 로컬은 Compose, 테스트는 Testcontainers** ← AI 추천 — 실행 절차가 짧고, 테스트가 실제와 같은 DB 에서 돈다. 대신 Docker 가 있어야 하고 테스트가 컨테이너 기동만큼 느려진다.
- **(나) 로컬에 PostgreSQL 을 직접 설치** — Docker 가 필요 없다. 대신 설치·계정·DB 생성 절차를 문서로 안내해야 하고 사람마다 환경이 달라진다.
- **(다) 로컬은 Compose, 테스트는 H2 인메모리** — 테스트가 빠르다. 대신 H2 의 PostgreSQL 흉내는 차이의 일부만 구현돼 있어, Flyway 스크립트와 upsert 문법이 실제와 다르게 동작해도 테스트는 통과할 수 있다.

## Decision

**(가)** 를 택한다. 로컬 실행은 `compose.yml` 의 PostgreSQL 을 앱이 함께 띄우고, 테스트는 Testcontainers 로 PostgreSQL 컨테이너를 띄운다.
의존성은 매핑 저장을 구현할 때 추가한다([ADR-0006](0006-add-dependencies-when-needed.md)).

**이유** — 이 설계는 SQL 을 직접 쓰고 스키마도 SQL 스크립트로 만든다. 그 SQL 이 실제 PostgreSQL 에서 도는지가 테스트의 핵심인데,
H2 는 차이의 일부만 흉내 내므로 테스트가 통과해도 실제와 다를 수 있다. 로컬 실행도 접속 정보를 손으로 맞추는 단계가 없어진다.

## Consequences

**얻는 것**
- 실행 절차가 "Docker 를 켜고 Mock 을 띄운 뒤 앱 실행"으로 짧아진다
- 테스트가 Flyway 스크립트와 SQL 문법을 실제 DB 에서 검증한다
- DB 버전이 코드에 적힌 이미지 태그로 고정된다

**잃는 것**
- Docker 가 없는 환경에서는 앱도 테스트도 돌지 않는다. README 에 이 조건을 적는다
- 테스트가 컨테이너 기동만큼 느려진다
- 컨테이너를 공유하면 테스트가 앞선 테스트의 데이터를 본다. 격리를 데이터 정리로 얻어야 한다

**넘기는 것**
- 테스트 사이의 데이터 격리 방식(트랜잭션 롤백, 테이블 비우기 등)은 테이블을 만든 뒤 정한다

## Verification

`compose.yml` 이 있고 `app/build.gradle.kts` 에 docker-compose·Testcontainers 의존성이 있는지.
매핑 저장 테스트가 PostgreSQL 컨테이너에서 도는지. 테스트를 만들면 그 이름을 여기에 적는다.

## Discussion

해당 없음. 추천과 결정이 같았다.
