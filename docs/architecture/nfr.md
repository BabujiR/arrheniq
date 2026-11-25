# Non-Functional Requirements and Capacity

Traces to URS-NFR-001..008, TEN-*, SEC-*.

## 1. Sizing assumptions
| Dimension | MVP design point | Headroom target (no redesign) |
|---|---|---|
| Tenants per Cloud environment | 25 | 300 |
| Active studies per tenant | 1,000 | 10,000 |
| Time points per study | ~10 | 40 |
| Test results per time point | ~15 tests × up to 12 replicates | 30 tests × 30 replicates |
| Results rows per large tenant per year | ~150k test results | 1.5 M |
| Concurrent users per tenant | 20 | 200 |
| Audit events per tenant per year | ~1–3 M | 30 M |
| Documents per tenant per year | ~5k (≈ 5–10 GB) | 100 GB |

## 2. Performance targets
| Operation | Target | Notes |
|---|---|---|
| Core screens/API (lists, detail, save) | p95 < 300 ms server time at the design point (URS-NFR-002) | Keyset pagination; indexed read models |
| Study approval incl. schedule generation | p95 < 2 s for 3 conditions × 15 time points × 20 tests | Single transaction |
| Single-batch regression | < 1 s | Stats service |
| Multi-batch poolability (≤ 10 batches) | < 3 s | |
| Stability summary PDF (one study) | < 10 s (async job with progress) | |
| CSV import, 5,000 rows | < 60 s (async) | |
| Audit-trail search for one study | < 2 s | Index on (tenant_id, record_type, record_id) + business key |

## 3. Availability and resilience
| Item | Target |
|---|---|
| Monthly availability (Cloud) | ≥ 99.9 % excluding announced maintenance (URS-NFR-001); about 43 min of unplanned downtime per month allowed |
| Maintenance windows | ≤ 4 h/month, weekends US night; zero-downtime deploys are the norm (expand/contract) |
| App tier | ≥ 2 tasks across 2 AZs; health checks; auto-restart |
| Stats tier | ≥ 1 task (MVP); 2 for HA from the first production customer |
| Database | RDS Multi-AZ; automatic failover (~1–2 min) |
| RPO / RTO | ≤ 15 min / ≤ 4 h (URS-NFR-003); PITR + cross-region snapshot copies |
| Degraded mode | If stats is down, evaluations queue or fail with a clear message; all other functions keep working |

## 4. Observability
- **SLOs:** availability of `/api/*` (good = non-5xx), latency p95 per route group, job success rate, audit-chain verification = 100 %.
- **Dashboards:** per environment and per tenant (requests, errors, DB time, jobs).
- **Alerting** to the founder's phone (PagerDuty/Opsgenie free tier or AWS SNS) for SLO burn, failed backups, verification failures and security alerts.

## 5. Data retention
| Data | Default retention |
|---|---|
| GxP records + audit trail | Life of contract; exported at exit; no automatic deletion (URS-NFR-004) |
| Generated reports | Life of contract (Object Lock governance mode, tenant-configurable minimum) |
| Audit anchors | 15 years (Object Lock compliance mode) |
| Application logs | 90 days hot, 1 year archive |
| Security logs | 1 year |
| Backups | PITR 35 days; monthly snapshots 1 year |

## 6. Cost estimate (Cloud, AWS us-east-1), *planning figures to verify with the AWS Pricing Calculator*
| Component | Prod (MVP, ~5 tenants) | Notes |
|---|---|---|
| ECS Fargate: app 2 × (1 vCPU, 2 GB), stats 1–2 × (1 vCPU, 2 GB) | ~$120–180/mo | Graviton (ARM64) images |
| RDS PostgreSQL 16 Multi-AZ, db.t4g.medium, 100 GB gp3 | ~$130–170/mo | Upgrade to m7g.large at ~20 tenants |
| ALB + CloudFront + WAF | ~$50–80/mo | |
| NAT gateway (1) + VPC endpoints | ~$60–90/mo | |
| S3 (+ Object Lock, replication), Backup vault | ~$20–50/mo | Grows with documents |
| Cognito (B2B MAU-based; Essentials/Plus tier) | ~$20–100/mo | Depends on tier and MAU; VERIFY |
| Secrets Manager, KMS, CloudWatch/OTel backend | ~$40–100/mo | |
| **Prod total** | **≈ $450–800/mo** | |
| Dev + validation environments (smaller, single-AZ, scheduled off-hours) | ≈ $250–450/mo | |
| **All environments** | **≈ $700–1,250/mo** | |

Marginal infrastructure cost per additional Starter/Professional tenant: **≈ $10–40/month** (storage + compute share). At list prices, gross margin on subscriptions stays above 90 % before support and services.
Dedicated: ≈ $600–900/mo infrastructure per customer, covered by the $36k/yr uplift.

## 7. Compatibility
- Browsers: current Chrome, Edge, Firefox, Safari; tablets ≥ 10" (URS-NFR-005)
- Accessibility: WCAG 2.2 AA (URS-NFR-006)
- Locale: English UI; dates per site locale; i18n keys from day one (URS-NFR-008)
- Barcode scanners: any HID keyboard-emulation scanner (USB/Bluetooth)
