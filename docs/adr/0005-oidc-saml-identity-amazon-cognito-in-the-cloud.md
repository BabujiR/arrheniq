# ADR 0005: OIDC/SAML identity: Amazon Cognito in the cloud, customer IdP in private

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
No passwords in the app (URS-SEC-003). MFA and SSO are needed (URS-IAM-002). E-signatures need verifiable re-authentication (URS-ESG-002). Private customers will bring their own IdP. Cognito managed login supports the OIDC `prompt` parameter (`login`, `none`) since May 2025, plus `acr_values`/`max_age` for step-up authentication.

## Decision
The app is a standard OIDC relying party (Spring Security). **Cloud:** an Amazon Cognito user pool (Essentials/Plus tier) with managed login, MFA, and federation to customer IdPs (SAML/OIDC) per tenant. **Private/Dedicated:** direct OIDC to the customer IdP (Entra ID, Okta, ADFS, Keycloak). Re-authentication for signatures uses `prompt=login&max_age=0` and is **verified via the `auth_time` claim**. Local development and CI use Keycloak in a container as a test IdP.

## Consequences
+ AWS-native, low cost, no IdP to operate.
+ Code is IdP-agnostic, so Private works without changes.
− Re-authentication behaviour of federated upstream IdPs must be verified per customer (PQ script).
− Cognito pricing and tier features must be confirmed before GA (VERIFY).

## References
- AWS What's New, 16 May 2025: "Amazon Cognito now supports OIDC prompt parameter" (`login`, `none`; Essentials/Plus tiers).
- AWS Cognito Developer Guide: step-up authentication with `acr_values` and `max_age`.
- OpenID Connect Core 1.0 §3.1.2.1 (`prompt`, `max_age`) and §2 (`auth_time`).

## Alternatives considered
Auth0 (good B2B features, higher cost at scale; fallback option). Self-hosted Keycloak in the cloud (rejected for the MVP: operational burden for one person).
