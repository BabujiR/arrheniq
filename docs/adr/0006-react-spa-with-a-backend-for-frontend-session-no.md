# ADR 0006: React SPA with a backend-for-frontend session (no tokens in the browser)

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
Modern UX with data-heavy grids and charts (React 19, AG Grid, ECharts). Browser token storage is an XSS exfiltration risk. Legacy had cleartext transport and unauthenticated endpoints.

## Decision
The SPA (React 19, TypeScript, Vite, TanStack Query/Router) is served as static assets (CloudFront in the cloud, nginx container in private). Authentication uses the **BFF pattern**: the Spring app performs the OIDC code flow and keeps tokens server-side, and the browser holds only an `HttpOnly; Secure; SameSite=Lax` session cookie plus a CSRF token. Sessions are stored in PostgreSQL (Spring Session JDBC). The SPA and API share one origin (`/api` routed to the app).

## Consequences
+ No tokens in JavaScript; simpler CORS (same origin).
+ Server-side idle timeout and lock are enforceable (URS-IAM-004).
− Session storage in the DB (small table, cleaned by a job).

## Alternatives considered
SPA with PKCE and in-memory tokens (rejected: refresh-token handling in the browser, XSS exposure). Server-rendered UI (rejected: poor fit for grid-heavy UX).
