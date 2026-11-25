# ArrheniQ backend (Maven, Spring Boot 4.1, Java 25)

```
backend/
├── pom.xml          parent + aggregator (spring-boot-starter-parent 4.1.x, Spring Modulith BOM 2.1.x)
├── app/             arrheniq-app: Spring Boot modular monolith
│   ├── pom.xml
│   ├── src/main/java/com/arrheniq/
│   │   ├── ArrheniqApplication.java
│   │   └── kernel/  tenancy (TenantContext), security (deny-by-default)
│   ├── src/main/resources/
│   │   ├── application.yml, application-local.yml
│   │   └── db/bootstrap + db/migration (Flyway V1–V12)
│   └── src/test/    ModularityTests, TenantContextTest, ApplicationIT (Testcontainers) + SQL tests
└── migrator/        arrheniq-migrator: eSTAR migration CLI (+ 17 unit tests)
```

## Prerequisites
- JDK 25, Maven 3.9+
- Docker (for the `*IT` integration tests: PostgreSQL 16 via Testcontainers)

## Build and test
```bash
cd backend
mvn verify              # unit tests (Surefire) + integration tests (Failsafe, needs Docker)
mvn -DskipITs verify    # without Docker
```

## Run locally
1. Start PostgreSQL 16 and bootstrap it once (passwords must match `application-local.yml`):
   ```bash
   psql -U postgres -v owner_password=local-owner -v app_password=local-app -v platform_password=local-platform \
        -f app/src/main/resources/db/bootstrap/00_roles_and_database.sql
   ```
2. Run the app (Flyway migrates as `arrheniq_owner`; the app runs as `arrheniq_app`):
   ```bash
   mvn -pl app spring-boot:run -Dspring-boot.run.profiles=local
   ```
3. Check health: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`. Every other URL is denied until slice S0 adds the login (deny by default).

## First-build checklist (versions to confirm once dependencies download)
- [ ] Spring Boot: bump `4.1.0` in `pom.xml` to the latest 4.1.x patch
- [ ] Spring Modulith: `2.1.1` or the latest 2.1.x
- [ ] Testcontainers core artifact resolves through the Boot BOM
- [ ] Generate the Maven Wrapper: `mvn -N wrapper:wrapper` and commit `mvnw`, `mvnw.cmd`, `.mvn/`
- [ ] Check the db-scheduler and Spring Session table DDL in `V2__platform.sql` against the versions added in S0
