import { csrfToken } from '../api/http';

/** Browser-level navigation to the BFF endpoints (full page loads: the IdP round trip can't run in fetch). */

export function currentPath(): string {
  return window.location.pathname + window.location.search;
}

/**
 * Why the user sees the welcome screen: no session yet, the session ended (expired/revoked), or the
 * session belongs to another tenant.
 */
export type WelcomeReason = 'none' | 'expired' | 'revoked';

/** The ArrheniQ welcome screen (no session needed); its Sign in button continues to the IdP. */
export function goToWelcome(reason: WelcomeReason = 'none', returnTo: string = currentPath()): void {
  const params = new URLSearchParams({ returnTo });
  if (reason !== 'none') {
    params.set('reason', reason);
  }
  window.location.assign(`/welcome?${params.toString()}`);
}

export function goToLogin(returnTo: string = currentPath()): void {
  window.location.assign(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
}

/** What an e-signature will sign; the server binds the re-authentication to exactly this record version. */
export interface SigningIntent {
  recordType: string;
  recordId: string;
  recordVersion: number;
}

/** URL of the re-authentication start (backend AuthController.reauth). */
export function reauthUrl(purpose: 'SIGN' | 'UNLOCK', returnTo: string, intent?: SigningIntent): string {
  const params = new URLSearchParams({ purpose, returnTo });
  if (intent) {
    params.set('recordType', intent.recordType);
    params.set('recordId', intent.recordId);
    params.set('recordVersion', String(intent.recordVersion));
  }
  return `/auth/reauth?${params.toString()}`;
}

/**
 * Re-authentication: SIGN before an e-signature (with the record about to be signed), UNLOCK after the idle
 * lock (backend ReauthPolicy).
 */
export function goToReauth(purpose: 'SIGN', returnTo: string, intent: SigningIntent): void;
export function goToReauth(purpose: 'UNLOCK', returnTo?: string): void;
export function goToReauth(purpose: 'SIGN' | 'UNLOCK', returnTo: string = currentPath(), intent?: SigningIntent): void {
  window.location.assign(reauthUrl(purpose, returnTo, intent));
}

/**
 * Logout is a form POST (not fetch) so the browser follows the redirect to the IdP's logout page
 * (OIDC RP-initiated logout). The CSRF token goes in the `_csrf` field.
 */
export function logout(): void {
  const form = document.createElement('form');
  form.method = 'POST';
  form.action = '/auth/logout';
  const field = document.createElement('input');
  field.type = 'hidden';
  field.name = '_csrf';
  field.value = csrfToken() ?? '';
  form.appendChild(field);
  document.body.appendChild(form);
  form.submit();
}
