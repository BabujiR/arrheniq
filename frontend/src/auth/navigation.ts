import { csrfToken } from '../api/http';

/** Browser-level navigation to the BFF endpoints (full page loads: the IdP round trip can't run in fetch). */

export function currentPath(): string {
  return window.location.pathname + window.location.search;
}

export function goToLogin(returnTo: string = currentPath()): void {
  window.location.assign(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
}

/** Re-authentication: SIGN before an e-signature, UNLOCK after the idle lock (backend ReauthPolicy). */
export function goToReauth(purpose: 'SIGN' | 'UNLOCK', returnTo: string = currentPath()): void {
  window.location.assign(`/auth/reauth?purpose=${purpose}&returnTo=${encodeURIComponent(returnTo)}`);
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
