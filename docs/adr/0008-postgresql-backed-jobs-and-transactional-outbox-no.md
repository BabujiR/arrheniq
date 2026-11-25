# ADR 0008: PostgreSQL-backed jobs and transactional outbox; no message broker

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Notifications, report generation, imports, anchors and webhooks need asynchronous processing. A broker (SQS/Kafka/RabbitMQ) adds infrastructure that Private customers would also have to run.

## Decision
Use **db-scheduler** (PostgreSQL-based) for scheduled and one-off jobs, with tenant context. Use a **transactional outbox** table for domain events that leave the transaction, dispatched by a job with at-least-once delivery and idempotent handlers.

## Consequences
+ No extra infrastructure; identical in Cloud and Private.
+ Exactly-once business effects via outbox + idempotency keys.
− Throughput limited by the DB (adequate for the target scale; revisit above ~100 jobs/second).

## Alternatives considered
Amazon SQS/EventBridge (rejected: cloud lock-in for Private). Kafka (rejected: operational overkill).
