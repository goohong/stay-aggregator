---
topic: 빌드 도구·프레임워크 버전
checked: 2026-09-14
---

# 빌드 도구·프레임워크 버전 조사

[ADR-0007](../adr/0007-build-and-framework-versions.md) 에서 Spring Boot·Kotlin·Gradle·JVM 버전을
정할 때 확인한 사실과 출처다. 판단은 ADR 에 있다.

## Spring Boot

| 확인한 것 | 값 | 출처 |
|---|---|---|
| 최신 릴리스 | 4.1.1 (4.0.8, 3.5.16, 3.4.7 도 존재) | Maven Central 메타데이터 [1] |
| OSS 지원 종료 | 4.1: 2027-07-31 / 4.0: 2026-12-31 / 3.5: 2026-06-30 / 3.4: 2025-12-31 | endoflife.date [2] |
| 4.1.1 시스템 요구사항 | Java 17–26, Gradle 8.14 이상 및 9.x, Spring Framework 7.0.9 이상 | 공식 문서 [3] |
| 4.0 에서 바뀐 것 | `starter-web` → `starter-webmvc`, WebClient 전용 `starter-webclient` 신설, Jackson 3, `@MockBean` 제거(`@MockitoBean`), `@SpringBootTest` 가 MockMvc 를 자동 제공하지 않음 | 마이그레이션 가이드 [4] |
| `starter-webclient` 4.1.1 이 끌고 오는 것 | `spring-boot-starter`, `spring-boot-starter-jackson`, `spring-boot-reactor`, `spring-boot-webclient`, `reactor-netty-http`. 리액티브 서버 없음 | POM [5] |
| 4.1.1 BOM 이 관리하는 버전 | Kotlin 2.3.21, Jackson 3.1.5, Jackson 2 3.x 병행용 2.21.5 | BOM POM [6] |
| Jackson 3 용 Kotlin 모듈 좌표 | `tools.jackson.module:jackson-module-kotlin:3.1.5` (옛 좌표 `com.fasterxml.jackson.module` 은 Jackson 2) | Maven Central [7] |

## Kotlin

| 확인한 것 | 값 | 출처 |
|---|---|---|
| 최신 릴리스 | 2.4.20 | Maven Central 메타데이터 [8] |
| 지원 정책 | 별도 LTS 명칭 없음. 2.4.0 부터 릴리스 라인마다 18개월 지원 (2.4 라인은 2027-12-03 까지) | 릴리스 문서 [9] |
| Kotlin Gradle 플러그인의 Gradle 지원 상한 | 2.1.0–2.1.10: 8.10 / 2.2.x: 8.14 / 2.3.0–2.3.10: 9.0.0 / 2.3.20–2.3.21: 9.3.0 / 2.4.0–2.4.10: 9.5.0 / **2.4.20: 9.7.0** | 호환표 [10] |
| 2.2 에서 바뀐 것 | `kotlinOptions {}` 제거(오류) → `compilerOptions {}` | What's new 2.2 [11] |
| 2.3·2.4 에서 확인한 것 | Spring 연동 관련 변경 없음. 2.3 에서 `when` 완전성 검사 안정화 | What's new 2.3 [12], 2.4 [13] |

## Gradle

| 확인한 것 | 값 | 출처 |
|---|---|---|
| 최신 릴리스 | 9.7.1 | 배포 API [14] |
| Gradle 실행에 필요한 JVM | 9.4 와 9.7 모두 17–26. 27 은 미지원 | 호환 문서 [15] |
| 9.7.1 패치 내용 | 9.7.0 대비 버그 수정 6건, 그중 하나가 Kotlin DSL 의 애너테이션 인자 순서 문제 | 릴리스 노트 [16] |
| 없는 JDK 자동 다운로드 | 설정 파일에 툴체인 저장소 플러그인(`foojay-resolver-convention`)이 있어야만 동작 | 툴체인 문서 [17] |

## JDK 25

| 확인한 것 | 값 | 출처 |
|---|---|---|
| 지원 | 대부분의 배포처에서 LTS | OpenJDK [18] |
| 병렬 작업 관련 기능 | Structured Concurrency(JEP 505) 는 다섯 번째 Preview, Scoped Values(JEP 506) 는 정식 | OpenJDK [18] |

## 실측 (로컬에서 빌드·기동)

| Gradle | Spring Boot | Kotlin | JVM 타깃 | 결과 |
|---|---|---|---|---|
| 8.14 | 3.4.5 | 2.1.0 | 21 | 빌드·기동 성공 |
| 9.4.1 | 3.5.3 | 2.2.0 | 21 | 빌드·기동 성공 |
| 9.4.1 | 4.1.1 | 2.2.0 | 21 | 빌드·기동 성공 |
| 9.4.1 | 4.1.1 | 2.4.20 | 21 | 빌드·기동 성공, stdlib 도 2.4.20 으로 해석 |
| **9.7.1** | **4.1.1** | **2.4.20** | **21** | 빌드·기동 성공, `--warning-mode all` 경고 없음 |

## 출처

1. https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter/maven-metadata.xml
2. https://endoflife.date/api/spring-boot.json
3. https://docs.spring.io/spring-boot/system-requirements.html
4. https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
5. https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-webclient/4.1.1/spring-boot-starter-webclient-4.1.1.pom
6. https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom
7. https://repo1.maven.org/maven2/tools/jackson/module/jackson-module-kotlin/3.1.5/jackson-module-kotlin-3.1.5.pom
8. https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/maven-metadata.xml
9. https://kotlinlang.org/docs/releases.html
10. https://kotlinlang.org/docs/gradle-configure-project.html
11. https://kotlinlang.org/docs/whatsnew22.html
12. https://kotlinlang.org/docs/whatsnew23.html
13. https://kotlinlang.org/docs/whatsnew24.html
14. https://services.gradle.org/versions/current
15. https://docs.gradle.org/current/userguide/compatibility.html
16. https://docs.gradle.org/9.7.1/release-notes.html
17. https://docs.gradle.org/9.4.1/userguide/toolchains.html
18. https://openjdk.org/projects/jdk/25/
