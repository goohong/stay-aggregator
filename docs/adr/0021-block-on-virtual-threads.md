---
id: 0021
title: 검색 요청은 가상 스레드에서 공급사 응답을 기다리기로 결정
status: accepted
date: 2026-09-15
---

# 0021. 검색 요청은 가상 스레드에서 공급사 응답을 기다리기로 결정

## Context

서버는 Spring MVC 이고 공급사 호출은 WebClient 가 논블로킹으로 한다([ADR-0020](0020-mvc-server-with-webclient.md)).
컨트롤러는 공급사 호출 결과(`Mono`)를 받아 응답으로 돌려줘야 하는데, 그 사이 요청이 무엇을 붙잡고 기다릴지
정해야 한다. 한 요청에서 가장 오래 기다리는 곳은 공급사 응답이고, 길게는 타임아웃까지 간다.

확인한 사실

| 사실 | 원문 |
|---|---|
| MVC 는 컨트롤러가 돌려준 `Mono` 를 `DeferredResult` 처럼 다뤄, 요청 스레드를 놓아주고 나중에 ASYNC 디스패치로 응답한다 | [Spring MVC, Reactive Types](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html#mvc-ann-async-reactive-types) · [Async Spring MVC compared to WebFlux](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html#mvc-ann-async-vs-webflux) |
| Reactor 연산자는 따로 지정하지 않으면 앞 연산자가 돌던 스레드에서 이어 실행된다. 공급사 응답 뒤의 처리는 이벤트 루프에서 돈다 | [Reactor, Threading and Schedulers](https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html#:~:text=most%20operators%20continue%20working%20in%20the%20Thread%20on%20which%20the%20previous%20operator%20executed) |
| Reactor 가 논블로킹 스레드에서 막는 것은 `block()` 호출뿐이다. 그 검사가 이 클래스에만 있어 JDBC 같은 다른 블로킹 호출은 감지되지 않는다(감지는 BlockHound 같은 별도 도구가 한다). 이 마지막 문장은 소스에 적힌 것이 아니라 검사 위치에서 끌어낸 것이다 | [BlockingSingleSubscriber.java](https://github.com/reactor/reactor-core/blob/main/reactor-core/src/main/java/reactor/core/publisher/BlockingSingleSubscriber.java) 의 `blockingGet` 안 `Schedulers.isInNonBlockingThread()` 검사. `main` 브랜치라 줄 번호는 밀린다 |
| ThreadLocal 값은 기본적으로 리액티브 연산자 안에 다시 채워지지 않는다 | [Spring Boot, Context Propagation](https://docs.spring.io/spring-boot/reference/actuator/observability.html#actuator.observability.context-propagation) |
| `OncePerRequestFilter` 는 기본적으로 ASYNC 디스패치에서 다시 호출되지 않는다 | [OncePerRequestFilter Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html#shouldNotFilterAsyncDispatch()) |
| Tomcat 비동기 요청의 기본 타임아웃은 30초다 | [Tomcat 11 HTTP Connector, asyncTimeout](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html#:~:text=Servlet%20specification%20default%20of%2030000) |
| `block()` 은 `CountDownLatch` 로 기다리고, 가상 스레드는 `LockSupport` 대기에서 캐리어 스레드를 놓아준다 | [BlockingSingleSubscriber.java](https://github.com/reactor/reactor-core/blob/main/reactor-core/src/main/java/reactor/core/publisher/BlockingSingleSubscriber.java) 는 클래스 선언에서 `CountDownLatch` 를 상속한다 · [JEP 444, java.util.concurrent](https://openjdk.org/jeps/444#java-util-concurrent) |
| 가상 스레드는 빨라지는 게 아니라, 기다리는 작업이 많을 때 처리량을 늘린다 | [JEP 444, Using virtual threads vs. platform threads](https://openjdk.org/jeps/444#Using-virtual-threads-vs--platform-threads) |
| 가상 스레드를 켜면 Tomcat 요청 처리 실행기가 가상 스레드 실행기로 바뀌고, 스레드 풀 크기 설정은 더 이상 적용되지 않는다 | [TomcatVirtualThreadsWebServerFactoryCustomizer](https://github.com/spring-projects/spring-boot/blob/v4.1.1/module/spring-boot-tomcat/src/main/java/org/springframework/boot/tomcat/autoconfigure/TomcatVirtualThreadsWebServerFactoryCustomizer.java) · [Spring Boot, Virtual threads](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.virtual-threads) |
| Spring Boot 는 가상 스레드에 Java 24 이상을 강력히 권장하고, 가상 스레드는 데몬 스레드라 `spring.main.keep-alive` 를 권한다 | 위 Virtual threads 절 |
| HikariCP 7.0.2(Boot 4.1.1 관리 버전)는 가상 스레드 부하에서 커넥션 반납 중 `Thread.yield()` 스핀으로 캐리어 스레드를 모두 점유한 사례가 있고, 7.1.0 에서 고쳐졌다. JDK 버전과 무관하다 | [HikariCP issue #2398](https://github.com/brettwooldridge/HikariCP/issues/2398) · [CHANGES 7.1.0](https://github.com/brettwooldridge/HikariCP/blob/dev/CHANGES) · [Boot 4.1.1 의존성 목록](https://github.com/spring-projects/spring-boot/blob/v4.1.1/platform/spring-boot-dependencies/build.gradle) |
| HikariCP 는 풀이 가득 차면 연결을 기다리게 하고 기본 풀 크기는 10 이다 | [HikariCP README, maximumPoolSize](https://github.com/brettwooldridge/HikariCP#frequently-used) |

## Options

- **(가) 컨트롤러에서 `block()`, 플랫폼 스레드** — 컨트롤러 안은 평범한 동기 코드라 어디서 DB 를 호출해도 된다.
  대신 공급사 응답을 기다리는 동안 Tomcat 스레드가 묶이고, 동시 검색 수가 Tomcat 최대 스레드 수에서 막힌다.
  그 기본값은 200 이다([Tomcat 11 HTTP Connector](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html) 의 `maxThreads`: "If not specified, this attribute is set to 200").
  이 200 은 드러나지 않는 입장 제한 역할도 해서, 나중에 제한이 사라지면 그 뒤의 커넥션 풀 설계를 다시 해야 한다.
- **(나) 컨트롤러가 `Mono` 반환** — 기다리는 동안 Tomcat 스레드를 놓아준다. 대신 공급사 응답 뒤의 처리가
  이벤트 루프에서 돌아 그 안의 DB 호출이 조용히 이벤트 루프를 막을 수 있고, ThreadLocal(MDC)이 기본적으로 전파되지 않으며,
  필터가 ASYNC 디스패치에서 호출되지 않고, 테스트에 `asyncDispatch` 가 필요하며, Tomcat 30초 비동기 타임아웃과
  우리 타임아웃을 함께 맞춰야 한다.
- **(다) 컨트롤러에서 `block()`, 가상 스레드** ← AI 추천 — (가)의 코드 모양을 유지하면서 기다리는 동안 OS 스레드를
  놓아준다. 대신 입장 제한이 사라져 커넥션 풀 앞의 제한을 직접 설계해야 하고, HikariCP 를 관리 버전보다 올려야 하며,
  Boot 가 권장하는 Java 24 이상 런타임이 필요하다.

## Decision

**(다)** 를 택한다. 컨트롤러는 공급사 호출 결과를 `block()` 으로 기다리고, 요청 처리는 가상 스레드에서 한다
(`spring.threads.virtual.enabled=true`). 런타임은 JDK 25 로 올린다([ADR-0022](0022-jvm-25.md)).

HikariCP 는 JDBC 를 추가할 때 7.1.0 이상으로 둔다([ADR-0006](0006-add-dependencies-when-needed.md) 에 따라 지금 넣지는 않는다).

**이유** — 한 요청이 가장 오래 기다리는 곳은 공급사 응답이다. (다)는 그 시간 동안 요청이 OS 스레드를 붙잡지 않으면서,
블로킹 DB 호출을 어느 단계에서 호출해도 되는 동기 코드 모양을 유지한다. (나)의 약점은 모두 확인된 동작이고
조용히 드러나는 종류인 반면, (다)의 약점은 의존성 버전과 런타임을 올리는 것으로 좁혀진다.
동시 검색 요청이 몰릴 수 있다고 보고, 입장 제한을 드러나지 않는 스레드 수에 맡기지 않고 처음부터 명시적으로 설계한다.
요구사항은 요청당 호출 수만 말하고 동시 요청 수를 말하지 않아, 이 전제는 우리 판단이다.

## Consequences

**얻는 것**
- 공급사 응답을 기다리는 동안 요청이 OS 스레드를 붙잡지 않는다
- 요청 한 건이 한 가상 스레드 위에서 끝나 ThreadLocal(MDC)과 필터가 평소대로 동작하고, 테스트는 일반 MockMvc 로 한다
- 블로킹 DB 호출을 이벤트 루프 밖으로 옮기는 스케줄러를 따로 두지 않는다

**잃는 것**
- Tomcat 스레드 수라는 입장 제한이 사라진다. 요청이 DB 커넥션 풀과 WebClient 커넥션 풀로 그대로 몰리므로,
  요청 수준 제한·풀 크기·공급사 동시 호출 수의 관계를 따로 정해야 한다(Q23)
- 가상 스레드는 DB 대기 시간이나 DB 처리량을 줄이지 않는다. DB 가 병목이면 연결 대기 줄만 길어진다
- Boot 가 관리하는 HikariCP 버전을 덮어쓴다
- 검색 한 건 전체의 최대 시간을 Tomcat 비동기 타임아웃이 대신 정해 주지 않으므로 우리가 정한다(Q23)
- 숙소 목록 주기 동기화를 `@Scheduled` 로 구현하면 스케줄러도 가상 스레드가 되어 `spring.main.keep-alive` 가 필요하다
- Netty 내부에 pinning 이 생기는 곳이 있는지는 확인하지 않았다
- **매핑 읽기가 늦어지면 검색 전체가 그만큼 늦어진다** (2026-09-17 구현하며 확인).
  별도 스케줄러를 두지 않으므로 그 읽기는 요청 스레드에서 블로킹으로 일어나고, 검색 전체 타임아웃이 그 읽기는 취소하지 못한다.
  취소하려면 스케줄러를 두어야 하는데 그것은 이 결정이 하지 않기로 한 것이다. DB 가 응답하지 않는 상황은 커넥션 풀의 대기 타임아웃(`connectionTimeout`)이 다룬다

## Discussion

- **AI 주장과 근거** — 처음에는 (나)를 권했다. 요구사항을 다시 대조한 뒤에는 요구사항의 규모 언급이 요청당 호출 수에 관한 것이고
  동시 요청 수 요구는 없다는 이유로 (가)를 권했고, (가)와 (다)는 컨트롤러 코드가 같아 나중에 옮기면 된다고 했다.
  (다)에는 JVM 21 의 pinning 과 JDK 25 설치 부담을 약점으로 들었다
- **반박** — 이 도메인은 조회 요청이 동시에 몰리고 응답이 원활해야 한다. 가상 스레드가 없다는 전제로 쓴 코드가 나중에
  병목이 되어 일을 두 번 하게 되지 않나. (나)보다 위험도 비용도 낮은 (다)에 왜 계속 보수적인가.
  JDK 25 설치는 당연한 일이라 근거에 들어갈 필요가 없다
- **검증 결과**
  - "코드가 같아 나중에 옮기면 된다"는 틀렸다. (가)의 Tomcat 스레드 수는 입장 제한 역할을 해서 옮길 때 커넥션 풀 앞 제한을 다시 설계해야 한다
  - "동시 요청 요구가 없다"는 소극적 근거였다. 독립 검토를 맡은 다른 AI 도 결론은 (가)였지만 같은 근거를 약하다고 봤다
  - pinning 은 우리 경로에서 확인된 원인이 없고, 실제 위험은 JDK 와 무관한 HikariCP 스핀 문제였다
  - 사용자가 가상 스레드의 근거로 든 "DB 대기"는 맞지 않았다. 동시 DB 사용은 커넥션 풀 크기가 정하므로 가상 스레드는 대기 비용만 줄인다.
    가상 스레드가 실제로 줄이는 것은 공급사 응답을 기다리며 요청이 스레드를 붙잡는 시간이다
- **그래서 어떻게 바뀌었나** — 추천을 (다)로 바꾸고, 이유를 "DB 대기"가 아니라 "공급사 응답 대기"로 적었다.
  JDK 설치 부담은 약점에서 뺐다. 입장 제한과 요청 전체 시한을 Q23 으로 넘겼다

## Verification

`spring.threads.virtual.enabled: true` 가 있는지
  → `app/src/main/resources/application.yml`

검색 컨트롤러가 `Mono` 가 아닌 응답 타입을 돌려주는지
  → `SearchController.search` 의 반환 타입이 `SearchResponse`

HikariCP 가 7.1.0 이상인지
  → `app/build.gradle.kts` 의 `extra["hikaricp.version"] = "7.1.0"`
