plugins {
	kotlin("jvm") version "1.9.25"
	// @Component·@Transactional 등 스프링 빈 클래스를 open 으로 (프록시 생성용)
	kotlin("plugin.spring") version "1.9.25"
	// JPA 엔티티에 Hibernate 가 쓰는 인자 없는 생성자 생성
	kotlin("plugin.jpa") version "1.9.25"
	id("org.springframework.boot") version "3.5.16"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.herenas"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-batch")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-mysql")
	implementation("org.jsoup:jsoup:1.23.2")
	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Kotlin 1.9.25 = Spring Boot 3.5 가 관리하는 버전. IntelliJ 2023.1(Kotlin 플러그인 1.9)에서 2.x 라이브러리를 못 읽어 맞춤
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
	compilerOptions {
		// 스프링의 @Nullable 표시를 Kotlin 타입 시스템에 반영
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

// 지연 로딩 프록시가 엔티티를 상속할 수 있도록 open 으로
allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// 실행 가능한 jar 하나만 만들도록 plain jar 비활성화 (Dockerfile 에서 build/libs/*.jar 복사 시 혼동 방지)
tasks.named<Jar>("jar") {
	enabled = false
}
