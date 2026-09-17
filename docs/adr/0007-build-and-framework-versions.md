---
id: 0007
title: 빌드 도구와 프레임워크 버전 채택
status: accepted (JVM 타깃은 superseded by ADR-0022)
date: 2026-09-14
---

# 0007. 빌드 도구와 프레임워크 버전 채택

## Context

요구사항이 정한 하한은 Java 21 이상, Spring Boot 3.4 이상(4 버전대 허용), 빌드 도구 Gradle 이다.
그 위에서 어느 버전을 쓸지 정해야 한다.

확인한 사실 (Maven Central 메타데이터, 공식 문서, 실측). 출처는 [조사 기록](../research/build-versions.md) 에 있다.

- Spring Boot 최신 정식 릴리스는 4.1.1. OSS 지원 종료가 4.1은 2027-07-31, 4.0은 2026-12-31,
  3.5는 2026-06-30, 3.4는 2025-12-31로 **3.4·3.5는 이미 지원이 끝났다.**
  Spring Boot 는 Kotlin 처럼 라인마다 지원 기간을 두는 정책이다. 무료 지원에는 JDK 의 LTS 같은
  구분이 없고, 상용 구독에는 메이저의 마지막 마이너에 붙는 장기 지원이 따로 있다
  ([지원 기간 API](https://api.spring.io/projects/spring-boot/generations) 에서 3.5 의 상용 종료일만 2032 로 멀다).
- Spring Boot 4.1.1 공식 시스템 요구사항은 Java 17–26, Gradle 8.14+ 및 9.x.
- Spring Boot 4 는 WebClient 전용 스타터 `spring-boot-starter-webclient` 를 새로 낸다.
  실제로 무엇을 끌고 오는지 의존성 트리로 확인한 결과
  `spring-boot-starter`, `spring-boot-starter-jackson`, `spring-boot-reactor`,
  `spring-boot-webclient`, `reactor-netty-http` 뿐이고 **리액티브 서버가 들어오지 않는다.**
  Spring Boot 3 에서는 WebClient 를 쓰려면 `spring-boot-starter-webflux` 를 넣어야 하고,
  그 안에 쓰지 않을 리액티브 서버 의존성이 함께 들어온다.
- Kotlin 최신은 2.4.20. Kotlin Gradle 플러그인이 완전히 지원한다고 적은 Gradle 범위는
  7.6.3–9.7.0. Kotlin 은 2.4.0 부터 JVM 용 표준 라이브러리에 릴리스 라인마다 18개월
  보안 지원을 둔다. 별도의 LTS 명칭은 문서에서 찾지 못했다.
- Gradle 최신은 9.7.1. Gradle 실행에 필요한 JDK 범위는 9.4.1 과 9.7.1 이
  **동일하게 17–26**이다. 27 은 둘 다 지원하지 않는다. 9.7.1 릴리스 노트를 확인한
  결과 9.7.0 대비 버그 수정 6건이 포함되어 있고, 그중 하나가 Kotlin DSL 의
  `Option` 애너테이션 인자 순서 문제다. 그 여섯 건은 모두 9.7.0 에서 깨진 것을 되돌린 수정이다.
  9.7.1 자체가 새로 깨뜨린 것이 있는지는 릴리스 노트로 알 수 없고, 로컬 빌드·기동으로만 확인했다.
- JDK 25 의 JEP 목록을 확인한 결과, 이 설계가 쓸 만한 새 라이브러리 API 가 없다.
  팬아웃 구조에 가장 맞아 보이는 Structured Concurrency(JEP 505)는 25 에서도
  다섯 번째 Preview 로 정식이 아니고, 애초에 이 프로젝트가 쓰는 것은
  가상 스레드 기반 팬아웃이 아니라 Reactor 기반 팬아웃이라 관련이 없다.
- Gradle 이 로컬에 없는 JDK 를 자동으로 받아오려면 툴체인 다운로드 저장소가 설정돼 있어야 하고,
  이 저장소는 `settings.gradle.kts` 에 설정 플러그인을 적용해 추가한다. `foojay-resolver-convention` 이 대표적인 예다. 실측 중 자동으로 받아진
  것처럼 보인 사례가 있었는데, 확인해 보니 몇 달 전 다른 프로젝트 작업 때
  이미 캐시에 있던 JDK 를 발견한 것이었고 이번 프로젝트에서 새로 받아진 게
  아니었다. 플러그인 없이는 빌드하는 사람의 컴퓨터에 해당 JDK 가 없으면 빌드가 그대로
  실패한다.

**근거 위치** (자세한 표는 [조사 기록](../research/build-versions.md))

| 사실 | 원문 |
|---|---|
| Spring Boot 4.1.1 요구사항 Java 17–26, Gradle 8.14+·9.x | [Java](https://docs.spring.io/spring-boot/system-requirements.html#:~:text=Spring%20Boot%204.1.1%20requires%20at%20least%20Java%2017) · [Gradle](https://docs.spring.io/spring-boot/system-requirements.html#:~:text=Gradle%208.x%20%288.14%20or%20later%29%20and%209.x) |
| Boot 4 스타터 이름 변경, `starter-webclient` 신설 | [마이그레이션 가이드 스타터 표](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#user-content-starters) (표는 스타터를 나열할 뿐 "신설"이라고 적지 않는다. 신설이라는 사실은 [메타데이터](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-webclient/maven-metadata.xml) 의 최초 버전이 4.0.0-M1 이고 3.x 가 없는 것으로 확인) |
| Kotlin Gradle 플러그인 2.4.20 의 Gradle 상한 9.7.0 | [호환표](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin:~:text=the%20maximum%20fully%20supported%20version%20is%209.7.0) |
| Kotlin 표준 라이브러리 18개월 보안 지원 | [릴리스 문서](https://kotlinlang.org/docs/releases.html#standard-library-security-support:~:text=has%20an%2018%E2%80%93month%20support%20window%20for%20each%20release%20line) |
| Gradle 9.4.1·9.7.1 실행 JVM 17–26 | [9.4.1](https://docs.gradle.org/9.4.1/userguide/compatibility.html#:~:text=A%20JVM%20version%20between%2017%20and%2026%20is%20required%20to%20execute%20Gradle) · [9.7.1](https://docs.gradle.org/current/userguide/compatibility.html#java_runtime:~:text=A%20JVM%20version%20between%2017%20and%2026%20is%20required%20to%20execute%20Gradle) |
| Gradle 9.7.1 의 Kotlin DSL 관련 수정 | [릴리스 노트](https://docs.gradle.org/9.7.1/release-notes.html#:~:text=expects%20Option%20annotation%20arguments%20in%20a%20different%20order) |
| JDK 자동 다운로드는 툴체인 다운로드 저장소 설정이 필요 | [툴체인 문서](https://docs.gradle.org/9.4.1/userguide/toolchains.html#sec:provisioning:~:text=as%20long%20as%20a%20toolchain%20download%20repository%20has%20been%20configured) |
| JDK 25 LTS, JEP 505 는 다섯 번째 Preview | [JDK 25](https://openjdk.org/projects/jdk/25/#Features:~:text=505%3A%20Structured%20Concurrency%20%28Fifth%20Preview%29) |

## Options

### Spring Boot

- **(가) 3.5.16** — 자료가 압도적으로 많다. 대신 이미 지원이 끝났고, WebClient 만 쓰는데도
  쓰지 않을 리액티브 서버 의존성이 딸려온다.
- **(나) 4.1.1** ← AI 추천 — 지원 중이고, `starter-webclient` 가 "MVC 서버에서 WebClient 호출만
  쓴다"는 이 프로젝트의 설계와 의존성 구조로 맞아떨어진다. 대신 4.0 계열이라 자료가
  적고, 스타터 이름과 Jackson 메이저 버전이 바뀌어 예전 예제를 그대로 옮기면 안 맞는
  자리가 있다.

### Kotlin

Kotlin Gradle 플러그인(KGP)의 공식 Gradle 지원 범위표를 확인한 결과, 버전마다
지원하는 Gradle 상한이 다르다.

| KGP 버전 | Gradle 지원 상한 |
|---|---|
| 2.1.0–2.1.10 | 8.10 |
| 2.2.0–2.2.21 | 8.14 |
| 2.3.0–2.3.10 | 9.0.0 |
| 2.3.20–2.3.21 | 9.3.0 |
| 2.4.0–2.4.10 | 9.5.0 |
| 2.4.20 | 9.7.0 |

- **(가) 2.1.0** — 처음 골격을 만들 때 쓴 값, 근거 없이 선택됐다. Gradle 지원 상한이
  8.10 이라 우리가 고른 Gradle 9.7.1 과 짝이 전혀 안 맞는다.
- **(나) 2.4.20** ← AI 추천 — Gradle 지원 상한이 9.7.0 으로, 우리 Gradle(9.7.1)과
  한 패치 차이까지 좁혀진다. `--warning-mode all` 로 다시 빌드해 그 한 패치 차이가
  실제로 경고를 내는지 확인했고, 경고가 없었다. 그 밖에 2.2~2.4 사이의 변경사항
  (`kotlinOptions` 제거, `when` 완전성 검사 안정화 등)을 확인했으나 우리 프로젝트에
  영향을 주는 것은 없었다.

### Gradle

- **(가) 9.4.1** — Kotlin Gradle 플러그인이 완전히 지원한다고 문서에 적은 범위
  (7.6.3–9.7.0) 안이다. 대신 "이미 검증했다"는 관성일 뿐, 9.7.1 보다 나은 점을
  대지 못한다.
- **(나) 9.7.1** ← AI 추천 — 실행 JDK 범위가 9.4.1 과 같고, 9.7.0 대비 버그 수정을
  포함하며 그중 하나가 Kotlin DSL 관련이다. 실제로 빌드하고 앱을 기동해 문제없음을
  확인했다. 전환 비용은 래퍼 버전을 바꾸는 것 하나였다.

### JVM 타깃

> 이 부분은 [ADR-0022](0022-jvm-25.md) 가 대체한다. 아래는 결정 당시의 기록이다.

- **(가) 25** — 최신 LTS. Boot 4.1 의 지원 상한(26) 안이다.
- **(나) 21** ← AI 추천 — 이 설계가 22 이상에서 쓸 라이브러리 API 가 없다.
  툴체인 다운로드 저장소 플러그인을 넣지 않기로 했으므로(ADR-0007 Decision 참고) 빌드하는 사람의 컴퓨터에
  해당 JDK 가 미리 깔려 있어야 한다. 21 이 더 흔할 것으로 보았으나 배포 점유율 자료를 찾아보지는 않았다.

## Decision

**Spring Boot 4.1.1 / Kotlin 2.4.20 / Gradle 9.7.1 / JVM 타깃 21** 로 확정한다.

**Kotlin 버전은 독립적으로 고르는 게 아니라 Gradle 버전에 종속된 결정이다.** Gradle을
먼저 정하면 그 버전을 지원 범위에 담는 최신 KGP 라인이 정해진다. 이 프로젝트에서는
Gradle 9.7.1 을 먼저 정하고, 그걸 지원 범위에 가장 가깝게 담는 것이 2.4.20 이다.

툴체인 다운로드 저장소 플러그인(`foojay-resolver-convention` 등)은 넣지 않는다. 자동 다운로드가 편리하지만
빌드 시점에 네트워크가 필요해지고, 그 대신 README 에 "JDK 21 필요"를 명시한다.

**타깃과 런타임은 다른 결정이다.** 타깃 21 은 이 설계가 22 이상의 무엇도 쓰지 않는다는
뜻일 뿐, 25 위에서 돌리는 것을 막지 않는다. 나중에 가상 스레드를 도입하기로 하면
JDK 24 이상(JEP 491, `synchronized` 안에서의 가상 스레드 pinning 제거)으로
**런타임**을 올리는 것이 이득이 되는데, 지금은 가상 스레드를 쓰지 않으므로 해당 없다.

`jackson-module-kotlin` 은 Jackson 3 좌표(`tools.jackson.module:jackson-module-kotlin`)로
JSON 처리를 실제로 만들 때 추가한다(ADR-0006). 옛 좌표(`com.fasterxml.jackson.module`)는
Jackson 2 용이라 Boot 4 가 설정하는 Jackson 3 에 붙지 않는다. 어느 시점에 어떤 증상으로 드러나는지는 확인하지 않았다.

**이유** — Spring Boot 4 의 `starter-webclient` 가 이미 정한 "MVC 서버에서 WebClient 호출만"
이라는 설계를 설정이 아니라 의존성 구조로 강제해 준다.

## Consequences

**얻는 것**
- 지원 중인 버전으로 시작한다
- WebClient 를 쓰기 위해 리액티브 서버 의존성을 끌어왔다가 설정으로 누르는 과정이 없다
- 타깃과 런타임을 분리해 두어, 나중에 가상 스레드를 도입할 때 무엇을 올려야 하는지가
  이미 정리되어 있다

**잃는 것**
- Spring Boot 4 관련 자료가 3.x 보다 훨씬 적다. WebClient 가 처음인 상황에서 막히면
  검색한 답이 3.x 기준일 확률이 높고, 그게 맞는지 판단하는 부담이 생긴다
- 스타터 이름과 Jackson 메이저 버전이 바뀌었으므로, AI 가 만드는 코드가 옛 이름이나
  옛 Jackson 관용구를 섞어 넣을 위험이 있다. 컴파일 에러로 잡히는 것은 괜찮지만
  **컴파일은 되는데 동작이 다른 경우**가 위험하다. WebClient 의 자동 구성 타임아웃과
  공급사별로 직접 거는 타임아웃 중 무엇이 우선하는지는 아직 검증하지 않았고,
  WebClient 설정을 실제로 만들 때 테스트로 확인한다

## Discussion

- **AI 주장과 근거** — "Boot 3 에서 WebClient 를 쓰려면 webflux 스타터를 넣고
  `spring.main.web-application-type: servlet` 으로 서버를 눌러야 한다"고 여러 번 말했다
- **반박** — Boot 3 도 webmvc 와 webflux 가 클래스패스에 함께 있으면 자동으로 SERVLET
  으로 정한다. 그 설정은 필요 없다
  ([Boot 3.5.0 의 `WebApplicationType.deduceFromClasspath()`](https://github.com/spring-projects/spring-boot/blob/v3.5.0/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/WebApplicationType.java) 는
  WebFlux 지표가 있고 MVC 지표가 없을 때만 REACTIVE 를 돌려준다)
- **검증 결과** — 반박이 맞다. Boot 3 의 실제 단점은 그 설정이 필요해서가 아니라
  안 쓰는 리액티브 서버 의존성이 함께 들어오는 것이다
- **그래서 어떻게 바뀌었나** — Boot 4 를 고르는 이유를 "설정을 안 눌러도 된다"가 아니라
  "의존성 자체가 깨끗하다"로 고쳤다

---

- **AI 주장과 근거** — "`jvmToolchain(21)` 은 로컬에 그 JDK 가 없으면 빌드가 실패한다"고
  주장했다가, 이후 "Gradle 이 없는 JDK 를 자동으로 받아온다"며 스스로 뒤집었다
- **반박** — 실제로 무엇이 그 JDK 를 받아왔는지 확인이 필요하다는 지적을 받았다
- **검증 결과** — 직접 재확인한 결과 두 주장 다 부정확했다. 그 JDK 는 이번에 받아진 것이
  아니라 몇 달 전 다른 프로젝트 작업으로 이미 캐시에 있던 것이었다. 공식 문서로 확인한
  결과 자동 다운로드는 툴체인 다운로드 저장소를 설정 플러그인(`foojay-resolver-convention` 등)으로 넣어야만
  동작한다
- **그래서 어떻게 바뀌었나** — 플러그인을 넣지 않기로 했으므로, 빌드하는 사람의 컴퓨터에 JDK 가
  없으면 빌드가 실패하는 것이 맞는 결론이 됐다. 가장 널리 깔린 LTS 를 타깃으로 고르는
  근거가 여기서 나온다

---

- **AI 주장과 근거** — Gradle 을 9.7.1 로 올리는 것이 빌드하는 사람의 환경의 위험을 줄인다는
  주장이 있었다(Gradle 데몬 자체를 실행하는 JDK 지원 상한이 최신일수록 넓다는 것).
  이를 반박하며 9.4.1 을 유지하기로 했다
- **반박·검증** — 공식 호환표를 확인한 결과 9.4.1 과 9.7.1 의 실행 JDK 지원 범위가
  17–26 으로 **동일**해 그 논거는 성립하지 않았다. 그런데 "9.4.1 이 9.7.1 보다
  나은 점이 있느냐"는 재반문을 받았고, 답은 없었다. "이미 검증했다"는 관성이지
  9.4.1 을 선호할 근거가 아니었다
- **그래서 어떻게 바뀌었나** — 9.7.1 의 릴리스 노트를 확인해 9.7.0 대비 버그 수정을
  포함하고 그중 하나가 Kotlin DSL 관련이며, 전에 되던 것이 다시 깨진 항목은 없음을 확인했다.
  실제로 빌드·기동까지 검증한 뒤 9.7.1 로 바꿨다

---

- **AI 주장과 근거** — "Kotlin 2.4.20 은 최신이고 Boot 의 BOM 과 어긋나지 않는다"만을
  근거로 들었다
- **반박** — "그러니까 코틀린 버전 근거는?"이라는 재질문을 받았고, "최신이라 골랐다"는
  Gradle 버전에서 이미 한 번 기각당한 논리와 같다는 지적을 받았다. Gradle 9.4.1 을
  9.7.1 로 바꿀 때와 같은 방식으로 직접 검증해 달라는 요청이 있었다
- **검증 결과** — KGP 의 공식 Gradle 지원 범위표를 확인한 결과, 우리가 고른
  Gradle 9.7.1 을 지원 범위에 담을 수 있는 KGP 버전은 2.4.20 이 유일했다(그것도
  한 패치 차이로). 2.1.0 은 지원 상한이 8.10 이라 9.7.1 과는 범위가 크게 어긋난다.
  "최신이라서"가 아니라 "우리가 고른 Gradle 과 짝이 맞는 유일한 버전이라서"가
  실제 근거였다
- **그래서 어떻게 바뀌었나** — 근거 문장을 지원 범위표 기반으로 다시 썼다.
  결정(2.4.20)은 바뀌지 않았다

## Verification

정한 버전이 실제로 선언돼 있는지
  → `build.gradle.kts` 의 `kotlin("jvm") version "2.4.20"` 과 `id("org.springframework.boot") version "4.1.1"`,
    `gradle/wrapper/gradle-wrapper.properties` 의 `gradle-9.7.1-bin.zip`

그 버전으로 빌드와 기동이 되는지
  → `./gradlew :app:cleanTest :app:test` 와 `./gradlew :app:bootRun` (2026-09-17 실행)

툴체인 다운로드 저장소 플러그인을 넣지 않았는지
  → `settings.gradle.kts` 에 그 플러그인이 없다

처음 이 절에 적어 둔 `jvmToolchain(21)` 은 확인 대상이 아니다. 툴체인은 25 로 올렸다([ADR-0022](0022-jvm-25.md)).
