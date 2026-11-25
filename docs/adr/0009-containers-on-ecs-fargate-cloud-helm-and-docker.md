# ADR 0009: Containers on ECS Fargate (Cloud); Helm and Docker Compose for Private; OpenTelemetry

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Minimal operations for one founder; the same images must run in the customer's environment (deployment-options.md).

## Decision
**Cloud and Dedicated:** ECS Fargate (ARM64/Graviton), ALB, CloudFront + WAF, RDS PostgreSQL, S3, provisioned with Terraform; GitHub Actions for CI/CD; images signed with cosign. **Private:** a Helm chart (Kubernetes) and a Docker Compose bundle (single VM). **Observability:** OpenTelemetry SDKs export to a collector (CloudWatch/Grafana in the cloud, the customer's backend in Private).

## Consequences
+ No cluster to manage in the cloud MVP.
+ Portable packaging for Private.
− Two packaging formats to test (Helm, Compose), covered by CI smoke deployments in 1.1.

## Alternatives considered
EKS in the cloud (rejected for the MVP: cluster operations). AWS App Runner (rejected: limited networking control).
