# ADR 0017: Maven multi-module build for the backend

- **Status:** Accepted (decided by the founder)
- **Date:** 2026-11-25
- **Deciders:** Founder (decision), AI engineering agent (author)
- **Supersedes:** the backend build-tool part of ADR 0014 (Gradle Kotlin DSL)

## Context
ADR 0014 proposed Gradle (Kotlin DSL) for the Java backend. The founder decided the backend is a **Maven** project with Spring Boot, which matches the team's experience and the legacy toolchain familiarity.

## Decision
- `backend/pom.xml` is the aggregator and parent POM. It inherits from `spring-boot-starter-parent` **4.1.x** and imports the **Spring Modulith 2.1.x** BOM.
- Modules: `backend/app` (`arrheniq-app`, Spring Boot application) and `backend/migrator` (`arrheniq-migrator`, CLI).
- Java release 25; compiler `-Xlint:all -Werror`; Maven Enforcer requires JDK 25+ and Maven 3.9+.
- Tests: Surefire for unit tests (`*Test`, `*Tests`), Failsafe for integration tests (`*IT`, Testcontainers with PostgreSQL 16).
- Spring Boot 4 modular starters are used explicitly (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `spring-boot-starter-webmvc-test`, …).
- A Maven Wrapper (`mvnw`) is generated on the first machine with internet access (`mvn -N wrapper:wrapper`) and committed, so CI and every developer use the same Maven version.

## Consequences
+ Familiar, conventional build for Spring Boot; strong IDE support (IntelliJ, Eclipse, VS Code).
+ Dependency versions centrally managed by the Spring Boot and Spring Modulith BOMs.
− Less flexible scripting than Gradle (not needed for this project).
− The frontend (pnpm) and statistics service (uv) keep their own native tools; CI orchestrates all three.

## Alternatives considered
Gradle Kotlin DSL (ADR 0014; replaced by founder decision).
