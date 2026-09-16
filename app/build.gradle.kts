plugins {
    kotlin("jvm")
    // Kotlin 클래스는 기본이 final 이라 @Configuration 을 Spring 이 프록시로 감쌀 수 없다.
    // 언어 선택의 결과이므로 기능과 무관하게 필요하다 (ADR-0002, ADR-0006).
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.stayaggregator"
version = "0.1.0"

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
}

// Boot 가 관리하는 HikariCP 7.0.2 에는 가상 스레드 부하에서 커넥션 반납이 헛도는 문제가 남아 있다 (ADR-0021).
extra["hikaricp.version"] = "7.1.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")

    // 매핑 저장·조회는 JdbcClient 로 한다 (ADR-0032). DB 는 PostgreSQL (ADR-0018).
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    // 스키마는 Flyway 스크립트로 만든다 (ADR-0033). PostgreSQL 은 전용 모듈이 따로 필요하다.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // 로컬에서는 compose 파일의 PostgreSQL 을 앱이 함께 띄운다 (ADR-0035).
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 테스트는 실제 PostgreSQL 컨테이너에서 돈다 (ADR-0035).
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// compose 파일은 저장소 루트에 둔다. Boot 는 실행 디렉터리에서 그 파일을 찾는데,
// Gradle 은 모듈 디렉터리에서 실행하므로 루트로 맞춘다 (ADR-0035).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
