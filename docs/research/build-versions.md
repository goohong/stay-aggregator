---
topic: 빌드 도구·프레임워크 버전
checked: 2026-09-14 (출처 문장 재대조 2026-09-15)
---

# 빌드 도구·프레임워크 버전 조사

[ADR-0007](../adr/0007-build-and-framework-versions.md) 에서 Spring Boot·Kotlin·Gradle·JVM 버전을
정할 때 확인한 사실과 출처다. 판단은 ADR 에 있다.

**원문** 칸의 링크는 출처 페이지의 해당 문장이나 절로 바로 이동한다. Maven 메타데이터·POM·JSON 파일은 앵커가 없어 파일 링크만 적었다.

## Spring Boot

| 확인한 것 | 값 | 원문 |
|---|---|---|
| 최신 정식 릴리스 | 4.1.1 (4.0.8, 3.5.16, 3.4.7 도 존재). 2026-09-15 재대조 때 메타데이터의 최신 표시는 마일스톤 4.2.0-M1 | [메타데이터](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter/maven-metadata.xml) |
| OSS 지원 종료 | 4.1: 2027-07-31 / 4.0: 2026-12-31 / 3.5: 2026-06-30 / 3.4: 2025-12-31 | [endoflife.date JSON](https://endoflife.date/api/spring-boot.json) |
| 4.1.1 시스템 요구사항 | Java 17–26, Gradle 8.14 이상 및 9.x | [Java 범위](https://docs.spring.io/spring-boot/system-requirements.html#:~:text=Spring%20Boot%204.1.1%20requires%20at%20least%20Java%2017) · [Gradle 범위](https://docs.spring.io/spring-boot/system-requirements.html#:~:text=Gradle%208.x%20%288.14%20or%20later%29%20and%209.x) |
| 4.0 에서 바뀐 것 | `starter-web` → `starter-webmvc`, `starter-webclient` 신설 / Jackson 3 / `@MockBean` 제거(`@MockitoBean`) / `@SpringBootTest` 가 MockMvc 를 자동 제공하지 않음 | [스타터 표](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#user-content-starters) · [Jackson 3](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#user-content-upgrading-jackson:~:text=Spring%20Boot%20now%20uses%20Jackson%203%20as%20its%20preferred%20JSON%20library) · [@MockBean 제거](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#user-content-mockbean-and-spybean-removal:~:text=support%20has%20been%20removed%20in%20this%20release%2C%20in%20favor%20of%20%40MockitoBean) · [MockMvc](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#user-content-using-mockmvc-and-springboottest:~:text=will%20no%20longer%20provide%20any%20MockMVC%20support) |
| `starter-webclient` 4.1.1 이 끌고 오는 것 | `spring-boot-starter`, `spring-boot-starter-jackson`, `spring-boot-reactor`, `spring-boot-webclient`, `reactor-netty-http`. 리액티브 서버 없음 | [POM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-webclient/4.1.1/spring-boot-starter-webclient-4.1.1.pom) |
| 4.1.1 BOM 이 관리하는 버전 | Kotlin 2.3.21, Jackson 3.1.5, Jackson 2 병행용 2.21.5 | [BOM POM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom) |
| Jackson 3 용 Kotlin 모듈 좌표 | `tools.jackson.module:jackson-module-kotlin:3.1.5` (옛 좌표 `com.fasterxml.jackson.module` 은 Jackson 2) | [POM](https://repo1.maven.org/maven2/tools/jackson/module/jackson-module-kotlin/3.1.5/jackson-module-kotlin-3.1.5.pom) |

## Kotlin

| 확인한 것 | 값 | 원문 |
|---|---|---|
| 최신 릴리스 | 2.4.20 | [메타데이터](https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml) |
| 지원 정책 | 2.4.0 부터 **JVM 용 표준 라이브러리**에 릴리스 라인마다 18개월 **보안 지원** (2.4 라인은 2027-12-03 까지). 별도의 LTS 명칭은 문서 전체에서 찾지 못함 | [보안 지원](https://kotlinlang.org/docs/releases.html#standard-library-security-support:~:text=has%20an%2018%E2%80%93month%20support%20window%20for%20each%20release%20line) |
| Kotlin Gradle 플러그인의 Gradle 지원 상한 | 2.1.0–2.1.10: 8.10 / 2.2.x: 8.14 / 2.3.0–2.3.10: 9.0.0 / 2.3.20–2.3.21: 9.3.0 / 2.4.0–2.4.10: 9.5.0 / **2.4.20: 9.7.0** | [표와 상한 문장](https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin:~:text=the%20maximum%20fully%20supported%20version%20is%209.7.0) |
| 2.2 에서 바뀐 것 | `kotlinOptions {}` 사용이 오류로 격상 → `compilerOptions {}` | [원문](https://kotlinlang.org/docs/whatsnew22.html#breaking-changes-and-deprecations:~:text=raises%20the%20deprecation%20level%20of%20the%20kotlinOptions%7B%7D%20block%20in%20Gradle%20to%20error) |
| 2.3 에서 확인한 것 | `when` 의 데이터 흐름 기반 완전성 검사가 안정화 | [원문](https://kotlinlang.org/docs/whatsnew23.html#stable-features:~:text=Data%2Dflow%2Dbased%20exhaustiveness%20checks%20for%20when%20expressions) |
| 2.4 에서 확인한 것 | Spring 연동 관련 변경은 문서 전체에서 찾지 못함 (없다는 주장이라 인용할 문장 없음) | [페이지](https://kotlinlang.org/docs/whatsnew24.html) |

## Gradle

| 확인한 것 | 값 | 원문 |
|---|---|---|
| 최신 릴리스 | 9.7.1 | [배포 API](https://services.gradle.org/versions/current) |
| Gradle 실행에 필요한 JVM | 9.4.1 과 9.7.1 모두 17–26. 27 은 미지원 | [9.4.1 문서](https://docs.gradle.org/9.4.1/userguide/compatibility.html#:~:text=A%20JVM%20version%20between%2017%20and%2026%20is%20required%20to%20execute%20Gradle) · [현재(9.7.1) 문서](https://docs.gradle.org/current/userguide/compatibility.html#java_runtime:~:text=A%20JVM%20version%20between%2017%20and%2026%20is%20required%20to%20execute%20Gradle) |
| 9.7.1 패치 내용 | 9.7.0 대비 버그 수정 6건, 그중 하나가 Kotlin DSL 의 `Option` 애너테이션 인자 순서 문제 | [원문](https://docs.gradle.org/9.7.1/release-notes.html#:~:text=expects%20Option%20annotation%20arguments%20in%20a%20different%20order) |
| 없는 JDK 자동 다운로드 | **툴체인 다운로드 저장소가 설정돼 있어야** 동작하고, 저장소는 설정 플러그인으로 추가한다. `foojay-resolver-convention` 은 그 대표적인 예 | [다운로드 조건](https://docs.gradle.org/9.4.1/userguide/toolchains.html#sec:provisioning:~:text=as%20long%20as%20a%20toolchain%20download%20repository%20has%20been%20configured) · [저장소 절](https://docs.gradle.org/9.4.1/userguide/toolchains.html#sub:download_repositories) |

## JDK 25

| 확인한 것 | 값 | 원문 |
|---|---|---|
| 지원 | 대부분의 배포처에서 LTS | [원문](https://openjdk.org/projects/jdk/25/#:~:text=JDK%2025%20will%20be%20a%20long%2Dterm%20support%20%28LTS%29%20release%20from%20most%20vendors) |
| 병렬 작업 관련 기능 | Structured Concurrency(JEP 505) 는 다섯 번째 Preview, Scoped Values(JEP 506) 는 정식 | [JEP 505](https://openjdk.org/projects/jdk/25/#Features:~:text=505%3A%20Structured%20Concurrency%20%28Fifth%20Preview%29) |

## 실측 (로컬에서 빌드·기동)

| Gradle | Spring Boot | Kotlin | JVM 타깃 | 결과 |
|---|---|---|---|---|
| 8.14 | 3.4.5 | 2.1.0 | 21 | 빌드·기동 성공 |
| 9.4.1 | 3.5.3 | 2.2.0 | 21 | 빌드·기동 성공 |
| 9.4.1 | 4.1.1 | 2.2.0 | 21 | 빌드·기동 성공 |
| 9.4.1 | 4.1.1 | 2.4.20 | 21 | 빌드·기동 성공, stdlib 도 2.4.20 으로 해석 |
| **9.7.1** | **4.1.1** | **2.4.20** | **21** | 빌드·기동 성공, `--warning-mode all` 경고 없음 |
