# ADR 0004: Application-level, hash-chained audit trail written in the same transaction

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Part 11 §11.10(e) requires a secure, computer-generated, time-stamped audit trail that records who, what, when and why, without obscuring previous values. Legacy captured only some edits and relied on the client (BR-061).

## Decision
An `AuditService` in the kernel captures field-level diffs at flush time and writes `audit_event` rows in the same transaction, chained by SHA-256 per tenant (advisory lock for ordering). The DB role is INSERT/SELECT only, plus a trigger guard. GxP tables reject writes without an audit context. Daily anchors go to S3 Object Lock. Details in audit-esignature.md.

## Consequences
+ Business-meaningful entries (business keys, reasons) instead of raw row dumps.
+ Tamper evidence that can be verified independently.
− More code than DB-trigger auditing; must be complete, so it's guarded by the DB context check and tests.
− Per-tenant serialization of audit inserts (fine at the expected volume; monitored).

## Alternatives considered
Pure DB triggers / pgAudit (rejected: no business context or reasons, hard to present to users). Hibernate Envers alone (rejected: no hash chain, no reason enforcement). Event sourcing for all aggregates (rejected: complexity).
