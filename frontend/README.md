# ArrheniQ frontend

React 19 single-page app behind the backend-for-frontend session ([ADR 0006](../docs/adr/0006-react-spa-with-a-backend-for-frontend-session-no.md)).
Stack: TypeScript (strict), Vite 8, Mantine 9, TanStack Router + Query, AG Grid Community 36, Vitest.

## Run locally
Prerequisites: Node.js 22.12+ (or 24 LTS) and pnpm (`npm install -g pnpm` or `corepack enable`).

```powershell
# 1. local services and the backend (see docs/delivery/s0.md)
cd infra/compose; docker compose up -d
cd ../../backend; .\mvnw.cmd -pl app spring-boot:run "-Dspring-boot.run.profiles=local"

# 2. the SPA (another terminal)
cd frontend
pnpm install
pnpm dev            # http://localhost:5173
```
Open **http://localhost:5173** and sign in as `smanager` / `dev-password` (or `qapprover` to approve).
The Vite dev server proxies `/api`, `/auth`, `/oauth2` and `/login/oauth2` to the backend on :8080 without
changing the Host header, so cookies, CSRF and the OIDC redirect behave exactly like production (one origin).

| Command | What it does |
|---|---|
| `pnpm dev` | dev server with hot reload |
| `pnpm typecheck` | TypeScript, strict |
| `pnpm test` | unit tests (Vitest) |
| `pnpm build` | type check + production build into `dist/` |

## How it fits together
- `src/api/http.ts`: fetch wrapper. Sends `X-XSRF-TOKEN` from the `XSRF-TOKEN` cookie, turns problem responses into
  `ApiError` (stable backend `code` + field `errors`), reports 401s to the session provider.
- `src/auth/SessionProvider.tsx`: loads `/api/v1/me`; idle lock (`ARQ-AUTH-LOCKED`) shows the lock screen
  (Unlock = re-authentication), any other 401 goes to login. `can(permission)` only hides controls; the server
  enforces every permission and the site scope.
- Logout is a form POST to `/auth/logout` so the browser follows the redirect to the IdP's logout page.
- **E-signature (approve):** the SPA remembers the pending approval in `sessionStorage`, sends the user through
  `/auth/reauth?purpose=SIGN` (the IdP asks for the password again) and, back on the page, calls `…/approve` once
  within 5 minutes (`src/signing/pendingSignature.ts`, ADR 0019).
- Reasons for change: when the server answers `ARQ-AUDIT-010` (422) the form asks for a reason and resends.
