# Audit Trail and Electronic Signatures (Part 11 / Annex 11 design)

This is the most compliance-critical part of ArrheniQ. Requirements: URS-AUD-001..007, URS-ESG-001..004, URS-REV-003/005. Legacy gaps fixed: D5, BR-032, BR-045, BR-061, BR-064 (discovery).

## 1. Principles
1. **Every GxP write goes through the audit kernel, in the same database transaction.** No audit entry → no commit.
2. **Append-only and tamper-evident:** a hash chain per tenant, plus daily anchoring to write-once storage.
3. **Who / what / when / why**, with old and new values, captured by the server and never supplied by the client (except the user-typed reason).
4. **Records are versioned. Signatures bind to a specific version's content hash.**
5. **No hard deletes** of GxP records.

## 2. Classification of data
| Class | Examples | Audit | Versioning | Signature |
|---|---|---|---|---|
| GxP master | Test, product, specification, protocol, dictionary value, tenant settings | Full field-level | Yes (version table or versioned aggregate) | Approval (configurable) |
| GxP transactional | Study, schedule, pull, inventory entry, result, review, evaluation, generated report | Full field-level | Yes | Approval/review (configurable) |
| Security | Users, roles, assignments, API keys, logins, failed logins | Full | n/a | n/a |
| Non-GxP | UI preferences, column choices, notification read-state | None or minimal | No | No |

The classification is declared on the entity with an annotation (`@GxpRecord(type = "TEST_RESULT")`), and a build-time check verifies every JPA entity has a classification.

## 3. Audit event model
```sql
CREATE TABLE audit_event (
    tenant_id        uuid         NOT NULL,
    seq              bigint       NOT NULL,          -- per-tenant gapless sequence
    id               uuid         NOT NULL,
    occurred_at      timestamptz  NOT NULL,          -- DB clock (UTC), NTP-synced
    site_id          uuid,
    actor_user_id    uuid,                            -- null only for system jobs
    actor_name       text         NOT NULL,          -- printed name at the time
    actor_type       text         NOT NULL,          -- USER | API_KEY | SYSTEM | MIGRATION
    action           text         NOT NULL,          -- CREATE | UPDATE | DELETE(logical) | STATUS_CHANGE | SIGN | LOGIN | EXPORT | ...
    record_type      text         NOT NULL,          -- e.g. TEST_RESULT
    record_id        uuid         NOT NULL,
    record_version   bigint,
    business_key     text,                            -- e.g. "260041 / 12M / Assay"
    changes          jsonb        NOT NULL,          -- [{field, old, new}]  (field-level diff)
    reason           text,                            -- required by ReasonPolicy
    reason_code      text,
    correlation_id   text         NOT NULL,          -- request/job trace id
    client_info      jsonb,                           -- ip, user agent (security events)
    prev_hash        bytea        NOT NULL,
    hash             bytea        NOT NULL,          -- SHA-256(canonical(event) || prev_hash)
    PRIMARY KEY (tenant_id, seq)
);
```
- Stored in the `audit` schema. The `arrheniq_app` role has **INSERT and SELECT only**. `UPDATE`, `DELETE` and `TRUNCATE` are revoked, and a trigger raises an exception on update or delete as a second guard.
- `changes` holds a canonical JSON diff of business fields (technical fields excluded). Large values (e.g., text > 4 kB) are stored as hash + pointer.
- Migrated legacy history (from `EA_APL_EDIT_REASONS`, `EA_APP_USER_AUDIT`, `EA_MODULE_ACCESS_AUDIT`) is imported with `actor_type = MIGRATION`, the original timestamps in the payload, and a flag `legacy = true` (URS-MIG-003).

## 4. Capture mechanism
```mermaid
sequenceDiagram
    participant C as Command handler (application layer)
    participant K as AuditService (kernel)
    participant H as Hibernate flush listener
    participant D as PostgreSQL
    C->>K: begin(action, recordType, reason?) — validates ReasonPolicy
    C->>C: load aggregate, apply domain change
    H->>K: on flush: entity before/after state (dirty fields)
    K->>K: build field diff, canonicalize
    K->>D: SELECT pg_advisory_xact_lock(hash(tenant_id)) ; read last hash
    K->>D: INSERT audit_event (seq+1, prev_hash, hash)
    C->>D: COMMIT (business rows + audit rows atomically)
```
- **Completeness guard (DB level):** GxP tables have a `BEFORE INSERT/UPDATE/DELETE` trigger that checks `current_setting('app.audit_ctx', true)` is set for the transaction (set by `AuditService.begin`). A write without an audit context fails. This prevents an accidental bypass (e.g., a new repository call without auditing).
- **Hash chain serialization:** a transaction-scoped advisory lock per tenant serializes chain appends. At the expected volume (≪ 50 GxP writes/second per tenant), this isn't a bottleneck. Revisit if the metric `audit_lock_wait_ms` p95 exceeds 50 ms.
- **Reads** of GxP data aren't audited by default. Exports, report generation, and audit-trail exports **are** audited.

## 5. Reasons for change (server-enforced)
`ReasonPolicy` (tenant-configurable, versioned) defines when a reason is mandatory, for example:
| Situation | Reason required |
|---|---|
| Any change to an approved/effective record | Always (not configurable) |
| Logical deletion | Always |
| Manual pull-date change; pull reversal; extra pull | Always |
| Result change after submission for review | Always |
| Changes in draft status | Configurable (default: no) |

A missing reason returns **HTTP 422** with `reason_required`. A reason can be selected from a tenant list (`reason_code`) plus free text (URS-AUD-004).

## 6. Record versioning
- Each GxP aggregate has a `version` (optimistic locking) and a **content hash** = SHA-256 of a canonical JSON of its business state (`RecordHasher`).
- Master records with formal versions (spec, protocol) keep immutable version rows (`spec_version`). Transactional records rely on the audit trail for history, plus the `record_version` reference.
- **Logical delete:** `deleted_at`, `deleted_by` and a reason. Default queries exclude deleted rows; audit viewers can see them.

## 7. Audit trail review and export
- **Record history panel** on every GxP screen: the timeline of changes, with signatures inline (URS-AUD-006).
- **Audit search** by study, product, user, date range, record type. Exports to PDF/CSV are themselves audited.
- **Audit-trail review** (1.1): the reviewer signs "Audit trail reviewed" for a study/time point (URS-AUD-007).

## 8. Integrity verification and anchoring
- **Daily anchor job:** for each tenant, compute `(tenant_id, date, last_seq, last_hash)` and write it to an S3 bucket with **Object Lock in compliance mode** (retention 15 years). Anchors are also listed in the platform audit.
- **Verify tool** (`arrheniq-audit verify --tenant X --from … --to …`): recomputes the chain and compares it with the anchors. It runs weekly as a job, and alerts on mismatch. It's included in the OQ (inject a modified row in a test DB → detection).

## 9. Electronic signatures
### 9.1 Signature model
```sql
CREATE TABLE e_signature (
    tenant_id       uuid        NOT NULL,
    id              uuid        NOT NULL,
    record_type     text        NOT NULL,
    record_id       uuid        NOT NULL,
    record_version  bigint      NOT NULL,
    content_hash    bytea       NOT NULL,     -- RecordHasher at signing time
    meaning         text        NOT NULL,     -- AUTHORED | REVIEWED | APPROVED | AUDIT_TRAIL_REVIEWED | ...
    signer_user_id  uuid        NOT NULL,
    signer_name     text        NOT NULL,     -- printed name (Part 11 §11.50)
    signed_at       timestamptz NOT NULL,
    auth_time       timestamptz NOT NULL,     -- from ID token
    idp_issuer      text        NOT NULL,
    idp_acr         text,                      -- MFA evidence where available
    comment         text,
    status          text        NOT NULL,     -- VALID | SUPERSEDED (record changed later) | REVOKED (with reason)
    PRIMARY KEY (tenant_id, id)
);
```
Each signature also creates an `audit_event` with `action = SIGN`.

### 9.2 Signing flow (two-component re-authentication, §11.200)
1. The user clicks *Approve*. The server checks the permission, segregation of duties (signer ≠ author/enterer when configured, URS-REV-005) and the record state, then creates a **signing intent** (`intent_id`, record, version, content hash, meaning, `created_at`, expires in 5 minutes).
2. The UI opens the IdP in a popup: `authorize?prompt=login&max_age=0&nonce=<intent nonce>&login_hint=<user>`.
3. The IdP re-authenticates the user (password + MFA per the tenant policy).
4. The BFF callback validates the ID token: signature, `iss`, `aud`, `nonce` = intent, `sub` = session user, **`auth_time` ≥ intent `created_at`**.
5. The server re-reads the record. If its version or content hash has changed since the intent was created, signing fails ("record changed, review again").
6. In one transaction: write the signature, apply the state transition (e.g., result → APPROVED), and write the audit event.

Notes:
- Amazon Cognito managed login supports `prompt=login` for re-authentication and `max_age`/`acr_values` for step-up. The server **relies on `auth_time`**, not on `prompt` alone, because a client could strip `prompt`.
- For **federated customer IdPs** (Entra, Okta), Cognito forwards the prompt. Whether the upstream IdP really re-authenticates is verified **per customer during PQ** (test script provided). If an IdP doesn't comply, the tenant is configured for direct OIDC to that IdP, which is supported by the same code path.
- Series signing (several approvals in one session) is allowed within a configurable window after a successful re-authentication (Part 11 §11.200(a)(1)(i) continuous-session provision). The default is **0, i.e., re-authenticate for every signing event**, and it's configurable by the tenant per meaning.

### 9.3 Manifestation and binding
- **Screen:** "Approved by Jane Smith (jsmith) on 2027-03-14 15:02 EDT (19:02 UTC) — Meaning: Approved".
- **Reports and PDFs:** a signature manifestation block on the record and a signature page for signed reports (URS-ESG-001).
- **Binding (§11.70):** a signature is valid only while the record's current content hash equals the signed `content_hash`. Any later change sets the signature to `SUPERSEDED`, shows it as such, and puts the record back into the approval workflow.

### 9.4 Customer obligations (not system features; listed in the validation package)
- Customers submit the Part 11 §11.100(c) certification to FDA (e-signatures are legally binding equivalents of handwritten ones).
- Customers' SOPs cover account management, signature meanings and training.

## 10. Test strategy (OQ)
| Test | Proves |
|---|---|
| For each GxP command: an audit event exists with correct actor, action, field diffs, reason | URS-AUD-001/002 |
| Direct SQL update/delete on `audit_event` as `arrheniq_app` → denied | URS-AUD-003 |
| A write to a GxP table without an audit context → trigger error | Completeness |
| Tamper simulation → the verify tool detects it | URS-AUD-003 |
| Approve without a reason when required → 422 | URS-AUD-004 |
| Signing with a stale `auth_time` → rejected; signing after a record change → rejected | URS-ESG-002/003 |
| Record changed after signing → signature SUPERSEDED, record back to review | URS-ESG-003 |
| Same user enters and approves (SoD on) → blocked | URS-REV-005 |
