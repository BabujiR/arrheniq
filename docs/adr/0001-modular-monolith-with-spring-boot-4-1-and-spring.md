# ADR 0001: Modular monolith with Spring Boot 4.1 and Spring Modulith

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
One founder plus an AI agent must build and operate the product. The domain has about 13 cohesive modules with strong transactional consistency needs (e.g., study approval must create the schedule atomically, URS-STU-005). Microservices would multiply deployment, observability and data-consistency work.

## Decision
Build `arrheniq-app` as a single deployable modular monolith on Java 25 / Spring Boot 4.1. Modules are top-level packages verified by Spring Modulith (no cyclic dependencies, only `api` packages visible). Domain events between modules are handled in-transaction where GxP consistency requires it, and via the outbox otherwise. Virtual threads are enabled.

## Consequences
+ One build, one deployment, one database transaction for GxP workflows.
+ Module boundaries keep the option to extract services later.
− Requires discipline: boundary violations must fail the build (Modulith + ArchUnit tests).
− The whole app scales together (acceptable at the target scale, see nfr.md).

## Alternatives considered
Microservices per context (rejected: operational load, distributed transactions). Unstructured monolith (rejected: erodes quickly with AI-generated code).
