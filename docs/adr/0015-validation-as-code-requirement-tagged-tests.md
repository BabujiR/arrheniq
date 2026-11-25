# ADR 0015: Validation as code: requirement-tagged tests generate traceability and OQ evidence

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Each release must ship a vendor validation package (URS-VAL-001) without a large QA team.

## Decision
Requirements live as data (`validation/urs.yaml`, generated from URS.md). Tests carry requirement tags (`@Requirement("URS-…")` in Java, `@pytest.mark.urs("URS-…")` in Python, tags in Playwright). CI collects the results into a traceability matrix (URS → test → result) and an OQ evidence pack (HTML/PDF with environment, build digest, timestamps, pass/fail, screenshots for UI tests). The independent QA reviewer and the founder approve the release record with e-signatures in a release-management area of the repo (signed tag + approval record).

## Consequences
+ Evidence is produced on every release at near-zero marginal cost.
+ Coverage gaps (requirements without tests) are visible automatically.
− Test naming and tagging discipline is mandatory (enforced by a CI check: every MVP requirement needs at least one passing test).

## Alternatives considered
Manual test scripts in documents (rejected: unsustainable for one person).
