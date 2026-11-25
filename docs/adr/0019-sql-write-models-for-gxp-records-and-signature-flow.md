# ADR 0019: SQL write models for GxP records; e-signature flow for approvals

- **Status:** Accepted (proposed by AI agent; **pending founder review**: it changes ADR 0013)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)
- **Amends:** ADR 0013 (persistence), ADR 0016 (e-signature binding), audit-esignature.md §9.2

## Context
S0.5 is the first GxP feature (test library, products). ADR 0013 proposed JPA/Hibernate for aggregates.
Building it showed that for regulated records we want every write to be explicit: which columns change,
under which version, and exactly which fields go into the audit trail and the signature hash. Hibernate's
dirty checking, flush ordering and `@TenantId` handling of composite keys hide exactly those details, and
they would have to be re-verified in validation (OQ) for every entity.

## Decision
1. **GxP writes use `JdbcClient` with explicit SQL**, in the module's application service:
   `SELECT … FOR UPDATE` → version check (`409 ARQ-CONC-001` if stale) → validate → field diff → audit event →
   `UPDATE … SET …, version = version + 1 WHERE id = ? AND version = ?`, all in one transaction.
   JPA stays available (and its transaction manager binds the tenant), but new modules follow this pattern.
2. **SQL safety rule (refines ADR 0013):** values are always bound parameters (positional `?` or named);
   SQL text is assembled only from constants. No value is ever concatenated into SQL.
3. **Record content for signatures** = business fields only (no status, version or timestamps), hashed with
   the audit canonical JSON rules (`RecordHasher`). Approving doesn't change what was signed; any business
   change does, and supersedes the signature (§11.70).
4. **Approval flow (MVP variant of audit-esignature.md §9.2):** the SPA sends the user through
   `/auth/reauth?purpose=SIGN` (forced `prompt=login&max_age=0`, verified `auth_time`), then calls
   `POST …/approve` within 5 minutes. The server checks permission, status and segregation of duties first,
   then consumes the single-use evidence, re-reads the locked record and writes signature, status change and
   audit events in one transaction. **Deferred:** binding the re-authentication to one specific record
   (a signing intent carried in the OIDC `nonce`); today the evidence is bound to the user and the purpose.
5. **Segregation of duties for approvals:** the approver may not be the last person who changed the content
   (from the audit trail) or who submitted it.

## Consequences
+ The audit trail, the signature hash and the SQL are visible in one place per command; easier to review and
  to validate.
+ No ORM behaviour to qualify for GxP writes.
− More hand-written SQL per entity (mitigated by a fixed pattern and integration tests per module).
− The signing-intent binding (decision 4) must be added before the first pilot's PQ.

## Alternatives considered
- Keep JPA for writes with entity listeners producing the audit diff (rejected: the diff and the SQL would
  be in different places, and flush timing affects the audit order).
- jOOQ (good, type-safe; rejected for the MVP to avoid another dependency and code generation).
