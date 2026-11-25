# Architecture Decision Records

| ADR | Title | Status |
|---|---|---|
| [0001](0001-modular-monolith-with-spring-boot-4-1-and-spring.md) | Modular monolith with Spring Boot 4.1 and Spring Modulith | Accepted (pending review) |
| [0002](0002-separate-stateless-python-statistics-service.md) | Separate stateless Python statistics service | Accepted (pending review) |
| [0003](0003-shared-schema-multi-tenancy-with-postgresql-row.md) | Shared schema multi-tenancy with PostgreSQL row-level security | Accepted (pending review) |
| [0004](0004-application-level-hash-chained-audit-trail-written.md) | Application-level, hash-chained audit trail written in the same transaction | Accepted (pending review) |
| [0005](0005-oidc-saml-identity-amazon-cognito-in-the-cloud.md) | OIDC/SAML identity: Amazon Cognito in the cloud, customer IdP in private | Accepted (pending review) |
| [0006](0006-react-spa-with-a-backend-for-frontend-session-no.md) | React SPA with a backend-for-frontend session (no tokens in the browser) | Accepted (pending review) |
| [0007](0007-reports-as-html-templates-rendered-to-pdf-in.md) | Reports as HTML templates rendered to PDF in-process; Apache POI for Office formats | Accepted (pending review) |
| [0008](0008-postgresql-backed-jobs-and-transactional-outbox-no.md) | PostgreSQL-backed jobs and transactional outbox; no message broker | Accepted (pending review) |
| [0009](0009-containers-on-ecs-fargate-cloud-helm-and-docker.md) | Containers on ECS Fargate (Cloud); Helm and Docker Compose for Private; OpenTelemetry | Accepted (pending review) |
| [0010](0010-s3-compatible-object-storage-with-object-lock-for.md) | S3-compatible object storage with object lock for GxP documents | Accepted (pending review) |
| [0011](0011-versioned-deterministic-statistical-algorithms.md) | Versioned, deterministic statistical algorithms with input snapshots | Accepted (pending review) |
| [0012](0012-time-and-date-handling-utc-instants-site-local.md) | Time and date handling: UTC instants, site-local calendar dates | Accepted (pending review) |
| [0013](0013-persistence-jpa-for-aggregates-plain-sql-for-read.md) | Persistence: JPA for aggregates, plain SQL for read models; Flyway migrations | **Amended by 0019** (GxP writes via SQL) |
| [0014](0014-monorepo-with-gradle-kotlin-dsl-pnpm-and-uv-github.md) | Monorepo with Gradle (Kotlin DSL), pnpm and uv; GitHub Actions | **Partly superseded by 0017** (build tool) |
| [0015](0015-validation-as-code-requirement-tagged-tests.md) | Validation as code: requirement-tagged tests generate traceability and OQ evidence | Accepted (pending review) |
| [0016](0016-e-signature-binding-to-record-content-hash-with.md) | E-signature binding to record content hash with IdP re-authentication | Accepted (pending review) |
| [0017](0017-maven-multi-module-build-for-the-backend.md) | Maven multi-module build for the backend | Accepted |
| [0018](0018-browser-login-user-matching-and-per-request-authorization.md) | Browser login, user matching and per-request authorization | Accepted (pending review) |
| [0019](0019-sql-write-models-for-gxp-records-and-signature-flow.md) | SQL write models for GxP records; e-signature flow for approvals | Accepted (**pending founder review**) |

Template: Context → Decision → Consequences → Alternatives. A superseded ADR is never deleted; it's marked *Superseded by ADR nnnn*.
