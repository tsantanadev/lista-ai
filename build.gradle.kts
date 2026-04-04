plugins {
	id("java")
	id("org.springframework.boot") version "4.0.0"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
}

group = "com.listaai"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-liquibase")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.mapstruct:mapstruct:1.6.3")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.security:spring-security-oauth2-resource-server")
	implementation("org.springframework.security:spring-security-oauth2-jose")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("io.rest-assured:rest-assured:5.5.2") {
		// Spring Boot 4 forces Groovy 5.x via BOM, but RestAssured 5.5.2 requires Groovy 4.x
		exclude(group = "org.apache.groovy")
	}
	testImplementation("org.apache.groovy:groovy:4.0.24")
	testImplementation("org.apache.groovy:groovy-xml:4.0.24")
	testImplementation("org.apache.groovy:groovy-json:4.0.24")
	testImplementation("org.wiremock:wiremock-standalone:3.12.1")
	testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<JavaCompile> {
	options.compilerArgs.addAll(listOf("-Amapstruct.defaultComponentModel=spring"))
}

tasks.withType<Test> {
	useJUnitPlatform()
	jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
	classDirectories.setFrom(
		files(classDirectories.files.map {
			fileTree(it) {
				exclude(
					"**/ListaAiApplication*",
					// Domain model records (no logic)
					"**/domain/model/**",
					// JPA entities (no logic)
					"**/persistence/entity/**",
					// MapStruct-generated mappers (use explicit paths, not broad wildcard)
					"**/adapter/input/rest/mapper/**",
					"**/adapter/output/persistence/mapper/**",
					// REST DTOs (pure records, no branching logic)
					"**/adapter/input/rest/dto/**",
					// Port interfaces (no implementations to instrument)
					"**/port/input/**",
					"**/port/output/**",
					// Spring Data JPA repository interfaces (runtime-proxied, not instrumented)
					"**/persistence/repository/**",
					// Spring Boot configuration classes (no business logic)
					"**/infrastructure/config/**"
				)
			}
		})
	)
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.jacocoTestReport)
	classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
	violationRules {
		rule {
			limit { minimum = "0.90".toBigDecimal() }
		}
	}
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
