# ADR 0013: Persistence: JPA for aggregates, plain SQL for read models; Flyway migrations

- **Status:** Accepted, **amended by [ADR 0019](0019-sql-write-models-for-gxp-records-and-signature-flow.md)** (GxP writes use explicit SQL)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Rich aggregates with invariants and auditing hooks benefit from an ORM. Lists, dashboards and reports benefit from explicit SQL. The legacy system mixed string-built SQL everywhere (S-06).

## Decision
Use Spring Data JPA (Hibernate) for aggregate persistence (with `@TenantId`, optimistic locking and audit flush listeners). Use Spring `JdbcClient` with **named parameters only** for read models and reports. Database changes go through **Flyway** (forward-only, expand/contract), run by a separate owner role. A Semgrep rule bans string concatenation in SQL.

## Consequences
+ Right tool for each job; safe parameterization.
− Two data-access styles (documented conventions).

## Alternatives considered
jOOQ everywhere (good option; rejected to keep dependencies minimal for the MVP). JPA everywhere (rejected: awkward for reporting queries).
