---
id: 0017
title: 매핑은 서버형 관계형 DB 에 저장하기로 결정
status: accepted
date: 2026-09-15
---

# 0017. 매핑은 서버형 관계형 DB 에 저장하기로 결정

## Context

요구사항은 관계형 DB 를 1개 이상 쓰고, 그 저장 대상을 공급사 코드와 내부 식별자의 매핑으로 정해 두었다.
관계형 DB 를 다른 형태의 DB 로 대신할 수는 없다.

매핑이 앱을 다시 띄운 뒤에도 남아 있어야 내부 식별자가 유지되고([ADR-0011](0011-internal-id-random-uuid.md)),
앱을 띄울 때 숙소 목록을 받아오지 못해도 기존 매핑으로 검색을 이어갈 수 있다. 공급사 목록을 받아오는
시점은 앱 기동 시와 기본 하루 한 번이다([ADR-0013](0013-catalog-sync-on-startup-and-interval.md),
[ADR-0016](0016-catalog-sync-interval-default-daily.md)). Mock 공급사는 앱과 다른 모듈·프로세스로 띄운다
([ADR-0005](0005-separate-mock-module.md)).

[H2 공식 문서](https://www.h2database.com/html/features.html) 기준으로, 메모리 모드는 마지막 연결이 닫히면 내용이 사라지고, 파일 모드(내장)는 한 번에 한 JVM 만
DB 를 열 수 있으며, PostgreSQL·MySQL 호환 모드는 차이의 일부만 맞춰 준다.

| 사실 | 원문 |
|---|---|
| 메모리 모드는 마지막 연결이 닫히면 내용이 사라짐 | [In-Memory Databases 절](https://www.h2database.com/html/features.html#in_memory_databases:~:text=For%20an%20in%2Dmemory%20database%2C%20this%20means%20the%20content%20is%20lost) |
| 내장 모드는 한 번에 한 JVM 만 DB 를 열 수 있음 | [Connection Modes 절](https://www.h2database.com/html/features.html#connection_modes:~:text=a%20database%20may%20only%20be%20open%20in%20one%20virtual%20machine%20%28and%20class%20loader%29%20at%20any%20time) |
| 혼합 모드(`AUTO_SERVER`)면 여러 프로세스가 접근 가능 | [Automatic Mixed Mode 절](https://www.h2database.com/html/features.html#auto_mixed_mode:~:text=Multiple%20processes%20can%20access%20the%20same%20database) |
| 호환 모드는 차이의 일부만 맞춤 | [Compatibility 절](https://www.h2database.com/html/features.html#compatibility_modes:~:text=only%20a%20small%20subset%20of%20the%20differences%20between%20databases%20are%20implemented) |

## Options

- **(가) H2 메모리 모드** — 설치할 것이 없다. 대신 앱을 켤 때마다 매핑이 비어, 기동 시 목록 실패가 매번
  검색 불가로 이어지고 내부 식별자가 매번 새로 발급된다.
- **(나) H2 파일 모드** — 설치 없이 데이터가 남는다. 대신 앱을 여러 대로 늘리려면 구성을 바꿔야 하고,
  운영에서 쓰는 서버형 DB 와 SQL 이 조금씩 다를 수 있다.
- **(다) PostgreSQL 또는 MySQL 서버** ← AI 추천 — 실제 서비스와 같은 구성이고, 데이터가 남으며 앱을 여러 대로
  늘리기 쉽다. 대신 실행하는 사람에게 DB 서버(보통 Docker)가 필요하고 실행 절차가 늘어난다.
- **(라) 기본은 H2 파일 모드, 설정으로 서버형 DB** — 가볍게도 실제처럼도 띄울 수 있다. 대신 설정과 확인해야 할
  조합이 두 배가 된다.

## Decision

**(다)** 를 택한다. 매핑은 PostgreSQL 또는 MySQL 서버에 저장한다. 둘 중 무엇을 쓸지는 따로 정한다.

## Consequences

**얻는 것**
- 앱을 다시 띄워도 매핑이 남아, 내부 식별자가 유지되고 기동 시 목록 실패에도 기존 매핑으로 검색할 수 있다
- 앱을 여러 대로 늘려도 같은 매핑을 함께 쓸 수 있다

**잃는 것**
- 실행하는 사람에게 Docker 같은 DB 서버 실행 수단이 필요하고, README 의 실행 절차가 늘어난다

**넘기는 것**
- PostgreSQL 과 MySQL 중 무엇을 쓸지 (Q21)
- 기동 시 목록을 받아오지 못할 때의 처리는 "맨 처음이거나 새 공급사의 첫 목록인 경우"로 좁혀서 정한다 (Q11)
- 캐시·검색 이력·실패 격리 같은 선택 항목에 관계형 DB 외의 저장소를 더할지는 그 항목에서 정한다

## Discussion

- **AI 주장과 근거** — 매핑 저장 방식을 앞서 몇 번 오픈 질문으로 올리자고 했고, 그 과정에서 "메모리 DB 에 두면
  재시작마다 매핑이 빈다"고 말했다
- **반박** — 사용자는 서비스를 처음 띄울 때를 빼면 매핑은 항상 있는 것 아니냐, 당연히 관계형 DB 라고 생각했는데
  다른 의견이 있느냐고 물었다. 이어서 NoSQL 이나 다른 형태의 DB 는 선택지가 될 수 없는지 물었다
- **검증 결과** — AI 가 말한 메모리 DB 는 H2 메모리 모드로, 관계형 DB 안에서의 선택이었다. 관계형 DB 는 요구사항이
  정한 것이라 다른 형태로 대신할 수 없고, 매핑 데이터도 관계형 DB 와 잘 맞는다. 사용자의 "처음 이후 매핑은 항상
  있다"는 데이터가 남는 방식을 택할 때만 성립해서, 그 조건을 먼저 정하기로 했다
- **그래서 어떻게 바뀌었나** — Q11 보다 DB 선택을 먼저 정했고, 그 결과 Q11 의 범위가 좁아졌다

## Verification

매핑이 서버형 관계형 DB 에 남는지
  → `CatalogSyncIntegrationTest` 가 실제 PostgreSQL 컨테이너에서 돈다(`Containers` 설정)

앱이 그 DB 를 쓰는지
  → `compose.yml` 의 `postgres:18` 과 `app/build.gradle.kts` 의 `spring-boot-docker-compose`

앱을 다시 띄운 뒤에도 남는지는 아직 확인하지 않았다.
