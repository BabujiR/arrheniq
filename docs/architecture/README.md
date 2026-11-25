# Phase 3: Solution Architecture (ArrheniQ)

Status: **DRAFT v0.1** (2026-11-25). Inputs: `docs/discovery/*`, `docs/product/*` (URS v0.2, MVP v0.3, deployment-options), `CLAUDE.md`.

| Document | Content |
|---|---|
| [architecture-overview.md](architecture-overview.md) | C4 context / container / component views, tech stack, repository layout, request flow |
| [deployment-architecture.md](deployment-architecture.md) | Cloud (AWS), Dedicated and Private deployment views; environments; CI/CD; release rings |
| [bounded-contexts.md](bounded-contexts.md) | 13 domain modules + kernel: responsibilities, aggregates, events, APIs, dependencies, URS coverage |
| [multi-tenancy.md](multi-tenancy.md) | Tenant model, PostgreSQL row-level security, tenant context, provisioning, isolation tests |
| [audit-esignature.md](audit-esignature.md) | Part 11 design: audit events, hash chain, anchoring, reasons, record versions, e-signature flow |
| [security-architecture.md](security-architecture.md) | STRIDE threat model, controls, secure SDLC, legacy findings → controls |
| [nfr.md](nfr.md) | Performance, availability, RPO/RTO, scalability, and cost per tenant |
| [../adr/](../adr/) | Architecture Decision Records 0001–0017 |

## Architecture in one paragraph
ArrheniQ is a **modular monolith** (Java 25, Spring Boot 4.1, Spring Modulith) with a **stateless Python statistics service** (FastAPI) and a **React 19 SPA** served through a **backend-for-frontend session** (no tokens in the browser). All data lives in **PostgreSQL 16** with **row-level security per tenant**. GxP documents go to **S3-compatible object storage with object lock**. Every GxP write goes through one **audit and e-signature kernel** that writes hash-chained audit events in the same transaction. Background work uses a **PostgreSQL-backed job queue and transactional outbox**, with no message broker. Identity is **standard OIDC/SAML**: Amazon Cognito in the cloud, the customer's IdP in private installs. The same signed container images run in **Cloud (ECS Fargate), Dedicated, and Private (Helm / Docker Compose)** deployments.

## Design drivers (from Phases 1–2)
| Driver | Source | Architectural response |
|---|---|---|
| One founder + AI; minimal operations | D-06, D-08 | Modular monolith, managed AWS services, no broker, no Kubernetes in the cloud MVP |
| Cloud **and** private hosting from one build | D-03, URS-TEN-006/007 | Open-standard dependencies only; tenancy always on; config-driven adapters |
| Part 11 / ALCOA+ by construction | gap-analysis §2; URS-AUD/ESG | Central audit kernel, hash chain, DB guards, signature binding to record hash |
| Provably correct statistics | calculations.md; URS-EVA | Isolated versioned stats engine; deterministic; reference validation suite |
| Legacy security failures must never recur | security-findings.md | Deny-by-default, BFF sessions, no string SQL, server-generated storage keys |
| Validation evidence every release | URS-VAL-001 | Requirement IDs in tests; CI generates traceability and OQ evidence |

## URS coverage by architecture element
| URS area | Primary architecture element(s) |
|---|---|
| TEN (tenancy, deployment models) | multi-tenancy.md; deployment-architecture.md; ADR 0003, 0009 |
| IAM, SEC | security-architecture.md §3–5; ADR 0005, 0006 |
| AUD, ESG | audit-esignature.md; ADR 0004, 0016 |
| MD, PRO, STU, SCH, INV, LBL, RES, REV, OOT | bounded-contexts.md (masterdata, protocol, study, scheduling, labels, results) |
| EVA | `evaluation` module + `arrheniq-stats`; ADR 0002, 0011 |
| RPT, DSH | `reporting` module; ADR 0007, 0010 |
| INT, MIG | `integration`, `migration` modules + `arrheniq-migrator`; ADR 0008 |
| ADM | kernel `configuration` + `platform` (entitlements, license file) |
| NFR | nfr.md; deployment-architecture.md §6 |
| VAL | ADR 0015; deployment-architecture.md §5 (release flow with independent QA approval) |
| AI (release 2.0) | Planned `assistant` module: read-only, tenant-scoped retrieval over the app's query APIs; outputs labelled as drafts; never writes GxP records (URS-AI-001); no training on tenant data (URS-AI-003). Detailed design deferred to 2.0 |

## Open items for the founder (review before Phase 4)
1. **Approve or amend the 16 ADRs** (all marked "Accepted, pending founder review").
2. **Cognito tier and pricing:** confirm Essentials vs Plus (Plus adds threat protection) before GA. *VERIFY*
3. **Domain names:** `arrheniq.com` with tenant subdomains (`<tenant>.arrheniq.com`) is assumed.
4. **AWS Organization setup:** separate accounts for `dev`, `validation`, `prod`, and later per-Dedicated-customer accounts.
