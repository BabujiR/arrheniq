# ADR 0002: Separate stateless Python statistics service

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Shelf-life statistics (ICH Q1E regression, poolability, later mixed models and Arrhenius/ASAP) are best served by the scientific Python ecosystem (SciPy, statsmodels) and must be validated in isolation (calculations.md, URS-EVA-*). The legacy system had two inconsistent engines.

## Decision
Implement all statistics in `arrheniq-stats` (Python 3.12, FastAPI, NumPy, SciPy, statsmodels). It's stateless, has no database access, and is reachable only from the app with a short-lived service JWT. It's the **single** statistics engine for screens, reports and exports. It also renders report charts as SVG (matplotlib) so the PDFs match the computed values.

## Consequences
+ Statisticians can review readable Python; reference comparisons with R are straightforward.
+ Validated independently; versioned algorithms (ADR 0011).
− A second runtime to build and operate (two containers instead of one).
− Network hop (≪ 50 ms; acceptable).

## Alternatives considered
Java libraries (Apache Commons Math) (rejected: weaker for ANCOVA/mixed models, harder for statistician review). R/Plumber (rejected: smaller ops/tooling ecosystem; R stays as the validation reference).
