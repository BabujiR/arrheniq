# ADR 0010: S3-compatible object storage with object lock for GxP documents

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Attachments, generated reports, exports and audit anchors must be immutable and retained (URS-STU-008, RPT-005, AUD-003). Legacy stored BLOBs in Oracle and served files via user-supplied paths (S-02, S-05).

## Decision
Store documents in object storage through the **S3 API** only (S3 in the cloud; MinIO or another S3-compatible store with object locking in Private). Keys are server-generated (`tenant/<id>/<type>/<uuid>`), and the DB stores the metadata and SHA-256. Object Lock: governance mode for documents (tenant-configurable minimum retention); compliance mode for audit anchors. Downloads use short-lived presigned URLs issued after authorization. Per-tenant data keys (envelope encryption) enable crypto-shredding at tenant deletion.

## Consequences
+ Immutability enforced by the storage layer; DB stays small.
− Private customers need an S3-compatible store with object lock (documented requirement).

## Alternatives considered
Store in PostgreSQL (rejected: DB bloat, no WORM). Local filesystem (rejected: no immutability, legacy failure mode).
