# ADR 0011: Versioned, deterministic statistical algorithms with input snapshots

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Shelf-life results must be reproducible and traceable (URS-EVA-004/005). Legacy silently returned t = 0 or p = 0 on errors (calculations.md §3).

## Decision
Every algorithm has an ID and semantic version (e.g., `q1e.poolability@1.0.0`). Requests carry an immutable input snapshot (values, time points, limits, parameters) and its hash. Responses include the algorithm version, warnings and errors. **Invalid input raises explicit errors; there are no silent defaults.** Released algorithm versions are never changed, and a fix means a new version. Validation: reference datasets with expected results from R (and the legacy Java engine in parity mode) run in CI, and produce OQ evidence.

## Consequences
+ Re-runs are byte-for-byte reproducible.
+ A clean audit story for regulators.
− Multiple algorithm versions may need to stay deployable (old evaluations stay viewable from stored outputs; re-run only on the current version).

## Alternatives considered
Unversioned library calls (rejected: not reproducible).
