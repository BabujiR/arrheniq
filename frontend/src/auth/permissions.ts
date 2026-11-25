import type { Me } from '../api/types';

/**
 * UI-side permission check: decides only which controls are shown. The server enforces every permission
 * and the site scope again (URS-IAM-005).
 */
export function can(me: Me | undefined, permission: string): boolean {
  return me?.permissions.includes(permission) ?? false;
}
