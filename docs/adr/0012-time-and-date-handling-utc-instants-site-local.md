# ADR 0012: Time and date handling: UTC instants, site-local calendar dates

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Pull dates are calendar dates at a site; audit times are instants; legacy used server-local DATE with ambiguous time zones (data-model D4).

## Decision
Instants (audit, signatures, status changes) are `timestamptz` stored in UTC and displayed in the site time zone with the offset (URS-NFR-007). Calendar concepts (on-stability date, pull dates, manufacture date) are `date` values interpreted in the site's time zone and holiday calendar. The DB server clock is the time source for audit timestamps (NTP-synced by RDS).

## Consequences
+ No DST ambiguity on GxP timestamps.
− Every UI and report needs explicit formatting helpers (provided in the kernel and the frontend library).

## Alternatives considered
Store local times (rejected: ambiguous across sites/DST).
