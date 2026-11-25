# ADR 0003: Shared schema multi-tenancy with PostgreSQL row-level security

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Multi-tenant SaaS (URS-TEN-001) plus Dedicated/Private single-tenant installs from the same code (URS-TEN-006). A one-person team can't run a database per tenant at small scale.

## Decision
Use a shared database and schema. Every tenant-owned table has `tenant_id`, composite PK/FKs `(tenant_id, id)`, and `ENABLE` + `FORCE ROW LEVEL SECURITY` with a policy on `current_setting('app.tenant_id')`, set per transaction. The app role has no BYPASSRLS. Hibernate `@TenantId` is used as a second layer. A schema linter and cross-tenant test suite run in CI (multi-tenancy.md).

## Consequences
+ Cheap and simple to operate; one migration path.
+ Database-enforced isolation that fails closed.
− Every query pays a small RLS cost (negligible with `tenant_id`-leading indexes).
− Noisy-neighbour risk, mitigated by quotas and jobs.

## Alternatives considered
Schema per tenant (rejected: migration fan-out, connection pool sprawl). Database per tenant (reserved for Dedicated/Private).
