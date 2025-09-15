plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.spring") version "2.2.10"
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.2.10"
}

group = "com.dddheroes"
version = "0.0.1-SNAPSHOT"
description = "Cinema.EventSourcing.VerticalSlice.Kotlin.Axon4.Spring"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["axonFrameworkVersion"] = "4.12.1"
extra["axoniqConsoleVersion"] = "1.9.3"
extra["assertkVersion"] = "0.28.1"
extra["springDocOpenApiVersion"] = "2.8.13"
extra["springBootVersion"] = "3.5.5"
extra["springModulithVersion"] = "1.4.1"
extra["springAiVersion"] = "1.0.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.axonframework:axon-spring-boot-starter")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springDocOpenApiVersion")}")
    implementation("org.axonframework.extensions.kotlin:axon-kotlin")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.5.0")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("io.axoniq.console:console-framework-client-spring-boot-starter:${property("axoniqConsoleVersion")}")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.axonframework:axon-test")
    testImplementation("com.willowtreeapps.assertk:assertk:${property("assertkVersion")}")
    testImplementation("io.rest-assured:spring-mock-mvc-kotlin-extensions")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
        mavenBom("org.axonframework:axon-bom:${property("axonFrameworkVersion")}")
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
