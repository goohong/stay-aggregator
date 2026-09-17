---
id: 0002
title: 구현 언어로 Kotlin 채택
status: accepted
date: 2026-09-14
---

# 0002. 구현 언어로 Kotlin 채택

## Context

Java 21+ 와 Kotlin 중 하나를 골라야 한다. 빌드는 어느 쪽이든 Gradle Kotlin DSL 을 쓴다.

작성자는 Kotlin + Spring Boot 로 멀티모듈 서비스를 약 10개월간 개발한 경험이 있으나,
최근 2년은 Java 로만 작업했다. 이 프로젝트에서 쓸 Spring WebClient 는 어느 언어로도 처음이다.

## Options

- **(가) Java 21** — 현재 주력이라 새로 익힐 것이 Reactor 하나로 줄어든다.
  record 와 sealed interface 로 실패 분류와 요금 모델을 표현하는 데 부족하지 않다.
  대신 Kotlin 작업물이 남지 않는다.
- **(나) Kotlin** ← AI 추천 — 공급사 응답 DTO 가 많은 구조에서 data class 와 null 안전성이
  코드량을 줄인다. 대신 2년 공백이 있는 언어와 처음 쓰는 WebClient 를 동시에 다루게 된다.

## Decision

**(나) Kotlin + Spring Boot** 로 구현한다.

**이유** — 최신 Kotlin 작업물로 남기고 싶고, 감을 다시 잡고 싶다.

## Consequences

**얻는 것**
- 공급사 응답을 다루는 경계 계층에서 nullable 을 타입으로 드러낼 수 있다.
  이 프로젝트는 필드 누락을 실패로 분류해야 하므로, 그 판정을 타입으로 강제하는 것이 도움이 된다

**잃는 것**
- Kotlin + Spring 조합의 알려진 설정 함정을 다시 밟을 수 있다.
  `kotlin("plugin.spring")` 은 언어 선택의 결과이므로 골격에 넣는다.
  `kotlin("plugin.jpa")` 와 `allOpen`, `jackson-module-kotlin` 은 각각 JPA 와 JSON 역직렬화를
  도입할 때 함께 넣는다 (ADR-0006)
- Reactor 체인은 구독 시점에 실행되므로 `runCatching` 으로 감싸도 예외가 잡히지 않는다.
  실패 처리는 `onErrorResume` 으로만 한다

## Discussion

- **AI 주장과 근거** — Java 를 권했다. 근거는 "AI 가 생성한 Kotlin 관용구를 나중에 방어하기 어렵다"
- **반박** — 설명 가능성을 구현의 기준으로 두면 지금의 이해도가 코드 품질의 상한이 된다.
  모자란 부분은 공부하면 된다
- **검증 결과** — 반박이 타당하다. 그 근거를 빼면 남는 것은 2년 공백뿐이고,
  10개월간 1만 줄 규모를 작성한 경험에 비추어 결정적이지 않다
- **그래서 어떻게 바뀌었나** — AI 가 추천을 Kotlin 으로 바꿨고, 작성자는 다른 이유로 같은 선택을 했다

## Verification

구현이 Kotlin 으로 되어 있는지
  → `git ls-files '*.kt'` 가 23개, `git ls-files '*.java'` 가 0개다(2026-09-17). 빌드 스크립트도 모두 `.gradle.kts` 다

Kotlin 버전이 한 곳에서 정해지는지
  → `build.gradle.kts` 의 `kotlin("jvm") version "2.4.20" apply false`. 두 모듈은 버전 없이 플러그인만 적용한다

Spring 이 Kotlin 클래스를 다룰 수 있게 해 주는 플러그인이 있는지
  → `build.gradle.kts` 가 `kotlin("plugin.spring")` 의 버전만 선언하고(`apply false`),
    `app/build.gradle.kts` 와 `mock-supplier/build.gradle.kts` 가 각각 적용한다

처음 이 절에 적어 둔 `kotlin("plugin.jpa")` 와 `allOpen` 은 확인 대상이 아니다. 매핑 저장을 JPA 로 하지 않기로 했다([ADR-0032](0032-mapping-persistence-with-jdbcclient.md)).
