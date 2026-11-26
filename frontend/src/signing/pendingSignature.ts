/**
 * Bridges the IdP round trip of an e-signature (ADR 0019): before redirecting to /auth/reauth?purpose=SIGN the
 * SPA remembers what the user is about to sign; after returning it applies the approval once. Lives in
 * sessionStorage (this tab only) and expires with the server's evidence lifetime (5 minutes).
 */

export interface PendingSignature {
  recordType: 'PRODUCT' | 'SPEC_VERSION' | 'PROTOCOL_VERSION';
  recordId: string;
  expectedVersion: number;
  comment: string;
  createdAt: number;
}

const KEY = 'arrheniq.pendingSignature';
export const MAX_AGE_MS = 5 * 60_000;

type KeyValueStore = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

function store(): KeyValueStore | undefined {
  try {
    return globalThis.sessionStorage;
  } catch {
    return undefined;
  }
}

export function rememberPendingSignature(p: PendingSignature, s: KeyValueStore | undefined = store()): void {
  s?.setItem(KEY, JSON.stringify(p));
}

/** Returns and removes the pending signature for this record if it is still fresh (single use). */
export function takePendingSignature(
  recordType: PendingSignature['recordType'],
  recordId: string,
  now: number = Date.now(),
  s: KeyValueStore | undefined = store(),
): PendingSignature | undefined {
  const raw = s?.getItem(KEY);
  if (!raw) {
    return undefined;
  }
  s?.removeItem(KEY);
  try {
    const p = JSON.parse(raw) as PendingSignature;
    const fresh = now - p.createdAt <= MAX_AGE_MS;
    return fresh && p.recordType === recordType && p.recordId === recordId ? p : undefined;
  } catch {
    return undefined;
  }
}
