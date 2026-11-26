# ADR 0018: Browser login, user matching and per-request authorization

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)
- **Refines:** ADR 0005 (identity), ADR 0006 (BFF session), ADR 0016 (re-authentication)

## Context
S0.4 implements the login described in ADR 0005/0006. Several details were open: how a person at the IdP
becomes a user of a tenant, where permissions are evaluated, what "idle lock" means on the server, and how
re-authentication is proven.

## Decision
1. **Tenant first, then user.** The tenant comes from the host (`<slug>.<base-domain>`) or, for Private/Dedicated
   and local, a fixed slug. A session is bound to one tenant; using it on another tenant's host ends it.
2. **The IdP authenticates; ArrheniQ authorizes.** A login is accepted only if `(issuer, subject)` matches an
   ACTIVE user of that tenant. A tenant admin *invites* users by e-mail (status INVITED, placeholder identity);
   the first login with the **same, verified e-mail** links the IdP identity (audited). Everything else is
   denied and audited with a reason; the browser sees only "access denied".
3. **Permissions are loaded from the database on every request**, never stored in the session. Deactivation,
   tenant suspension or a removed role take effect on the next request (URS-IAM-005/007). Cost: one indexed
   query per request.
4. **Session rules on the server** (URS-IAM-004): idle **lock** after `session.idle_lock_minutes` (requests are
   refused with `ARQ-AUTH-LOCKED` until the same user re-authenticates), idle **logoff** after
   `session.logoff_minutes` (session store expiry), and an absolute lifetime (default 12 h).
5. **Re-authentication is verified, not assumed** (Part 11 §11.200): the request carries `prompt=login&max_age=0`;
   on return the user must be the same tenant user and `auth_time` must be after the request. Otherwise the
   session is ended. Evidence is single-use and valid for 5 minutes for a signature.
6. **One OIDC client registration for the MVP** (`arrheniq`): the Cognito user pool in the cloud federates to
   customer IdPs; Private installs point the same registration at the customer IdP. Per-tenant client
   registrations are deferred until a customer needs a separate app client.

## Consequences
+ Clear separation: the IdP can't grant access to ArrheniQ data, and an admin can revoke access instantly.
+ Every security event (login, denial, re-authentication, lock, expiry, revocation, logout) is in the tenant's
  hash-chained audit trail.
− ~~Spring Session JDBC stores the session with Java serialization~~ **Resolved in S1.1:** session attributes
  are stored as JSON (`SessionJson`, bean `springSessionConversionService`): `<class name>\n<JSON>`, only
  allow-listed classes are written or read, nested types are checked by Spring Security's Jackson type validator
  (extended with `ArrheniqUser`). Migration V13 removed the old Java-serialized sessions (everyone signed in once
  more). Test: `SessionJsonTest`; end to end: `KeycloakLoginIT`.
− Failed logins at the IdP itself (wrong password) are only visible in the IdP's logs; lockout is the IdP's job
  (URS-IAM-003). Forwarding IdP events into the audit trail is a follow-up.

## Alternatives considered
- Roles from IdP groups/claims: rejected for the MVP; site-scoped GxP roles must be administered and audited in
  ArrheniQ, and customer IdP group hygiene varies. Group-to-role mapping can be added for Enterprise SSO later.
- Caching permissions in the session: rejected (stale access after revocation).
- Just-in-time user creation on first login: rejected (anyone in a federated IdP could create an account).
