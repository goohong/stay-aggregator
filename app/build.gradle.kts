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

    // 서버는 Spring MVC 다 (ADR-0020). 요청은 가상 스레드에서 처리하고 공급사 응답을 block 으로 기다린다 (ADR-0021).
    implementation("org.springframework.boot:spring-boot-starter-web")
    // 공급사 호출은 WebClient 로 한다 (ADR-0020). 이 스타터는 리액티브 서버를 끌고 오지 않는다 (ADR-0007).
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    // Kotlin 데이터 클래스로 JSON 을 받으려면 필요하다. Boot 4 는 Jackson 3 이라 tools.jackson 좌표를 쓴다 (ADR-0007).
    implementation("tools.jackson.module:jackson-module-kotlin")

    // 매핑 저장·조회는 JdbcClient 로 한다 (ADR-0032). DB 는 PostgreSQL (ADR-0018).
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    // 스키마는 Flyway 스크립트로 만든다 (ADR-0033). PostgreSQL 은 전용 모듈이 따로 필요하다.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // 로컬에서는 compose 파일의 PostgreSQL 을 앱이 함께 띄운다 (ADR-0035).
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4 는 @WebMvcTest 같은 테스트 슬라이스를 기술별 모듈로 나눴다. 스타터가 끌고 오지 않아 따로 넣는다.
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    // 테스트는 실제 PostgreSQL 컨테이너에서 돈다 (ADR-0035).
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x 부터 모듈 이름이 `testcontainers-<제품>` 으로 바뀌었다. 예전 이름은 1.21.x 에서 멈춰 있다.
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// compose 파일은 저장소 루트에 둔다. Boot 는 실행 디렉터리에서 그 파일을 찾는데,
// Gradle 은 모듈 디렉터리에서 실행하므로 루트로 맞춘다 (ADR-0035).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
