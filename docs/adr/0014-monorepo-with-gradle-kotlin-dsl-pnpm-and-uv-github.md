# ADR 0014: Monorepo with Gradle (Kotlin DSL), pnpm and uv; GitHub Actions

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
One founder plus AI benefit from a single repository with shared docs, validation data and atomic cross-cutting changes.

## Decision
A single repository `arrheniq` with `backend/` (Gradle Kotlin DSL; modules `app`, `migrator`), `stats/` (uv-managed Python), `frontend/` (pnpm), `infra/`, `validation/`, `golden/` (encrypted or private submodule), and `docs/`. GitHub Actions workflows per component with path filters, plus a release workflow that builds all images with one version tag.

## Consequences
+ One place for code, requirements, tests and evidence (traceability).
− CI must use path filters to stay fast.

## Alternatives considered
Polyrepo (rejected: overhead, harder traceability).
