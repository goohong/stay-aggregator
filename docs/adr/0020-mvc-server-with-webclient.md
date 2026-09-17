---
id: 0020
title: 서버는 Spring MVC 로 두고 공급사 호출에만 WebClient 채택
status: accepted
date: 2026-09-15
---

# 0020. 서버는 Spring MVC 로 두고 공급사 호출에만 WebClient 채택

## Context

요구사항은 공급사 호출에 WebClient 를 지정하지만 서버 전체를 WebFlux 로 만들 것까지 요구하지 않는다.
MVC 위에서 WebClient 만 써도 되고, 어느 쪽이든 이유를 남기라고 한다.

검색 한 건의 흐름은 매핑 조회(DB) → 공급사별 재고·요금 조회(WebClient, 병렬) → 정규화·병합이다.
이 중 오래 걸리는 구간은 공급사 응답을 기다리는 부분이고, 매핑 조회는 인덱스를 탄 짧은 조회다.

## Options

- **(가) WebFlux** — 서버부터 DB 접근까지 한 가지 논블로킹 방식으로 맞춘다. 대신 DB 접근도 논블로킹이어야
  이점이 살아서 JDBC 대신 R2DBC 를 써야 하고, 컨트롤러·서비스·저장소 코드 전체를 Reactor 방식으로 짜야 한다.
- **(나) Spring MVC 서버 + 공급사 호출에만 WebClient** ← AI 추천 — 오래 걸리는 공급사 호출은 WebClient 로
  이미 논블로킹이다. 서버와 DB 접근은 익숙한 서블릿·JDBC 방식으로 둔다. 대신 한 요청 안에서 요청 처리
  스레드와 WebClient 의 이벤트 루프라는 두 실행 문맥이 섞인다.

## Decision

**(나)** 를 택한다. 서버는 Spring MVC(서블릿)로 두고, WebClient 는 공급사 호출에만 쓴다.
DB 접근은 JDBC 로 블로킹 호출한다.

**이유** — 기다리는 시간이 긴 곳은 공급사 호출이고, 그 부분은 (나)에서도 논블로킹이다. (가)는 밀리초 단위의
매핑 조회까지 논블로킹으로 만들기 위해 DB 드라이버와 코드 전체의 방식을 바꾸는 비용을 치른다.

## Consequences

**얻는 것**
- 공급사 호출 흐름(동시 호출 수 제한, 타임아웃, 부분 실패)만 Reactor 로 다루고 나머지는 서블릿·JDBC 로 둔다
  ([ADR-0009](0009-reactor-over-coroutines.md))
- Spring Boot 4 의 `spring-boot-starter-webclient` 가 리액티브 서버 없이 WebClient 만 들여와 이 구조와
  의존성이 맞는다([ADR-0007 Context](0007-build-and-framework-versions.md#context))

**잃는 것**
- 한 요청 안에서 요청 처리 스레드와 이벤트 루프 스레드를 오간다. 이벤트 루프 위에서 블로킹 코드를 호출하지 않도록
  경계를 의식해야 한다
- 요청이 공급사 응답을 기다리는 동안 무엇을 붙잡고 있을지는 이 결정이 정하지 않는다.
  [ADR-0021](0021-block-on-virtual-threads.md) 에서 정한다

## Verification

`app/build.gradle.kts` 에 WebFlux 스타터가 없고 WebClient 스타터가 있는지.
