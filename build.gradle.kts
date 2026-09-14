// 버전만 선언하고 적용은 각 모듈에서 한다.
// 공통 블록으로 플러그인과 의존성을 상속시키면 모듈마다 원치 않는 것이 딸려 들어간다 (ADR-0005).
plugins {
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.spring") version "2.4.20" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}
