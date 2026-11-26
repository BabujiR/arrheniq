# S1 walkthrough: master data, about 10 minutes

The S1 demo for a subject-matter expert (SME) who knows eSTAR: from the test library through product, specification
and protocol to approval by electronic signature, with the audit trail behind every step. Each step lists who is
signed in, what to do and what to point out. The questions at the end are what we want the SME to answer.

## Before the session (5 minutes, once)

1. Start from a clean database so the demo data is fresh:
   ```
   cd infra/compose
   docker compose down -v
   docker compose up -d
   ```
   Wait until Keycloak answers on http://localhost:8180.
2. Start the backend with the local profile:
   ```
   cd backend
   .\mvnw.cmd -pl app spring-boot:run "-Dspring-boot.run.profiles=local"
   ```
   On the first start the log shows `Demo data created`. If it shows `Demo data incomplete`, the stack trace says
   why; recreate the database and start again.
3. Start the frontend: `cd frontend; pnpm dev`, open http://localhost:5173.
4. Use two browser windows, one normal and one private, so two users can be signed in at the same time.
   Every user's password is `dev-password`.

| User | Who | Can |
|------|-----|-----|
| `smanager` | Sam Manager, stability manager at New Jersey QC | create and edit tests, products, specifications, protocols; submit |
| `qapprover` | Quinn Approver, QA at New Jersey QC | approve and reject by e-signature |
| `tadmin` | Tara Admin, tenant administrator | users and settings; no GxP approvals |
| `bmanager` | Blake Manager, Tenant B | sees only Tenant B |

### What the demo data contains (tenant A, site NJ)

- **Test library:** Appearance, Assay (HPLC), Impurity B, Total impurities, Dissolution (Q at 30 min),
  Water content (KF), pH, each with its default limits, replicates and decimals.
- **Products:** MET-500 Metformin 500 mg film-coated tablets (submitted for approval), AML-5 Amlodipine 5 mg
  tablets (draft, shelf life not yet set), OND-4 Ondansetron oral solution (draft).
- **Specifications:** SPEC-MET-500 v1 with six tests and limits (submitted), SPEC-OND-4 v1 (draft),
  SPEC-AML-5 (empty draft).
- **Protocols for MET-500:** PRT-MET-500-LT, long-term 25 °C / 60 % RH over 36 months, from the ICH template
  (draft); PRT-MET-500-ACC, accelerated 40 °C / 75 % RH over 6 months (submitted).
- **Tenant B:** one test and one product (CIP-250), to show that tenants never see each other's data.

Nothing is approved in the demo data. Approval is an electronic signature, which needs a person to enter their
password again, so all approvals happen live in the walkthrough.

## The walkthrough

| # | Time | Who | Do | Point out |
|---|------|-----|----|-----------|
| 1 | 0:00 | `smanager` | Sign in. The home page lists **Drafts to finish**: AML-5, OND-4, the two draft specifications, the long-term protocol. | One list of everything waiting for me, across products, specifications and protocols. |
| 2 | 0:45 | `smanager` | Open **Test library**, then Assay (HPLC). | Default limits, alert limits, replicates and decimals live with the test and are copied into specifications. A retired test stays readable but can't be added anywhere new. |
| 3 | 1:30 | `qapprover` (second window) | Sign in. **Waiting for your approval** shows MET-500, SPEC-MET-500 v1 and PRT-MET-500-ACC v1. Open MET-500, click **Approve**, add a comment, sign in again when asked. | The password is asked again for this exact record version (21 CFR Part 11 §11.200). The signature shows printed name, date and time with the site time zone, and meaning (§11.50). |
| 4 | 3:00 | `qapprover` | Open SPEC-MET-500 v1 and approve it the same way. | Limits read like a specification: “95.0 – 105.0 %”, “NMT 0.20 %”, “NLT 80 %”, text for appearance. After approval the version is effective from today and can't change. |
| 5 | 4:00 | `smanager` | Open **Protocols**, then PRT-MET-500-LT. | Built from the ICH Q1A(R2) template (0, 3, 6, 9, 12, 18, 24, 36 months) and the tests of the specification. |
| 6 | 4:30 | `smanager` | In the grid: tick Dissolution at 3M, add a 48-month time point with **Add time point**, raise the units planned at 36M, then **Save draft**. | The test × time-point matrix replaces eSTAR's pull-point screens. Time points stay in date order; the total of units planned updates as you type; warnings flag a time point with tests but no units, or with nothing to run. |
| 7 | 5:30 | `smanager` | **Submit for approval**. Then, still as `smanager`, open PRT-MET-500-ACC (submitted by Sam) and look for an Approve button. | There is none: Sam has no approval rights. If a user had both roles, the server would still refuse, because the author or submitter can't approve their own work (segregation of duties). |
| 8 | 6:15 | `qapprover` | Approve PRT-MET-500-LT v1. | Approved protocol, effective today; the content (time points, grid, units) is hashed into the signature. |
| 9 | 7:00 | `smanager` | On PRT-MET-500-LT click **New version**, change the reserve units, add a change summary, save, submit. | A new version is a copy of the approved one. Only one open version at a time. |
| 10 | 7:45 | `qapprover` | Approve v2. Open the version list. | v1 is now superseded, v2 approved. Both stay readable with their signatures. |
| 11 | 8:30 | `smanager` | On the protocol page, open **History** for v2, then for v1. | Who did what and when, old → new for each cell that changed (for example “DISS · time points” or “36M · units planned”), reasons and signatures. The trail is append-only and hash-chained; nobody, administrators included, can edit it. |
| 12 | 9:15 | `qapprover` | Open PRT-MET-500-ACC v1 and **Reject** it with a reason. | A reason is mandatory; the version goes back to draft and the reason is in its history. |
| 13 | 9:45 | `bmanager` (either window, after signing out) | Sign in as Tenant B. | Only CIP-250 and Tenant B's own test exist here. Tenant isolation is enforced by the database itself (row-level security), not only by the screens. |

## Questions for the SME

1. Is the test library close enough to eSTAR's? Which test attributes are missing (method version, lab, instrument,
   reporting rules)?
2. Do the limits read the way your specifications are written? Which limit forms do you use that we can't show yet
   (ranges with different decimals, “report result”, conditional limits)?
3. Is the test × time-point grid how you think about a protocol? What else is planned per time point in your
   protocols (container orientation, sample sizes per test, extra pulls)?
4. Is the units-planned calculation right for you, or do you plan units per test and time point?
5. Which records must need one approver and which need two (the setting `approval.<kind>` can be
   none, single or double, per tenant or per site)?
6. Would QA accept this signature and history as Part 11-compliant evidence? What would an auditor ask that the
   history does not show?
7. What would stop you from moving your current eSTAR products and protocols here?

Write the answers into `docs/discovery/` (local only) so they shape S2.
