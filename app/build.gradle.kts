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

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
