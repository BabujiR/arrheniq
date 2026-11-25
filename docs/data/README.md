# Phase 4: Data (ArrheniQ)

Status: **v0.1** (2026-11-25). Inputs: docs/architecture (ADR 0003, 0004, 0010, 0012, 0013, 0016), the legacy eSTAR schema, and the URS.

| Deliverable | Location | Status |
|---|---|---|
| Database bootstrap (roles, database, pgcrypto) | `backend/app/src/main/resources/db/bootstrap/00_roles_and_database.sql` | Tested on PG 16 |
| Flyway migrations V1–V12 (60 tables in 3 schemas, RLS, audit guards, seed, provisioning) | `backend/app/src/main/resources/db/migration/` | Tested on PG 16 |
| Schema linter (10 checks) + security behaviour tests (T01–T21) | `backend/app/src/test/resources/db/` | **All passing** |
| Schema overview + table catalog | [schema.md](schema.md) | |
| Column-level legacy mapping (mapping v1.0.0) | [legacy-mapping.md](legacy-mapping.md) | |
| Migration tool design | [migration-design.md](migration-design.md) | |
| Migrator skeleton (Java): whitelist extractor, value/expiry/parameter parsers, reconciliation model, pipeline | `backend/migrator/` | Compiles on JDK 21 with `-Xlint:all -Werror`; **17 unit tests passing** |
| Seed / reference data | [seed-data.md](seed-data.md) | **VERIFY with the stability SME** |

## What was verified (2026-11-25, local PostgreSQL 16.15)
1. Bootstrap + V1→V12 apply in order as `arrheniq_owner` with no errors.
2. Schema linter: 0 violations (forced RLS everywhere, policies present, `tenant_id`-leading PKs, tenant-scoped FKs, all tables classified, audit triggers on GxP tables, no DELETE on GxP, append-only audit, no BYPASSRLS, the app role owns nothing). A meta-test confirmed the linter catches a deliberately bad table.
3. Behaviour tests: provisioning of two tenants; fail-closed reads without a tenant; isolation in both directions; cross-tenant insert rejected; cross-tenant FK rejected; GxP write without audit context rejected; hard delete denied; logical delete needs a reason; audit UPDATE/DELETE denied; audit chain gap rejected and a valid append accepted; the reference catalog is read-only to the app.

## Notes and open items
- The db-scheduler and Spring Session table DDL must be checked against the library versions pinned in Phase 6 (marked VERIFY in V2).
- The backend is a **Maven** multi-module project (ADR 0017): `backend/pom.xml` → `app` (Spring Boot 4.1) and `migrator`. The migrator tests are JUnit 5 sources run by Surefire.
- The ICH condition and template catalog needs SME review before release.
- Next schema changes: **V13+**. Always end a migration with `SELECT platform.apply_security();`.
