# ADR 0016: E-signature binding to record content hash with IdP re-authentication

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Part 11 §11.50, §11.70 and §11.200 require signature manifestation, binding to the record, and two-component authentication at signing.

## Decision
Signatures reference `(record_type, record_id, record_version, content_hash)` plus meaning, printed name, signed_at, and the IdP `auth_time`/issuer/ACR. Signing requires a server-side signing intent and an OIDC re-authentication (`prompt=login`, `max_age=0`), verified by `auth_time` ≥ intent creation. Any later change to the record marks the signature SUPERSEDED and sends the record back to the workflow. Series signing within a continuous session is configurable, with a default of re-authentication every time. Full design in audit-esignature.md.

## Consequences
+ Cryptographically meaningful binding; independent of IdP vendor.
− A popup re-authentication on each signature (UX cost, mitigated by batch approval of several records under one re-authentication within the same signing action).

## Alternatives considered
Password re-entry inside the app (rejected: the app must not handle passwords). PKI/digital certificates per user (rejected: heavy for customers; can be added later as an option).
