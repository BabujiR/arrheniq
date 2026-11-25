import { describe, expect, it } from 'vitest';
import { MAX_AGE_MS, rememberPendingSignature, takePendingSignature, type PendingSignature } from './pendingSignature';

function memoryStore() {
  const data = new Map<string, string>();
  return {
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => void data.set(k, v),
    removeItem: (k: string) => void data.delete(k),
  };
}

const pending: PendingSignature = { recordType: 'PRODUCT', recordId: 'p1', expectedVersion: 1, comment: 'ok', createdAt: 1000 };

describe('pending signature', () => {
  it('is returned once for the same record while fresh', () => {
    const s = memoryStore();
    rememberPendingSignature(pending, s);
    expect(takePendingSignature('PRODUCT', 'p1', 1000 + MAX_AGE_MS, s)).toEqual(pending);
    expect(takePendingSignature('PRODUCT', 'p1', 1000, s)).toBeUndefined();
  });

  it('is discarded when stale or for another record', () => {
    const s = memoryStore();
    rememberPendingSignature(pending, s);
    expect(takePendingSignature('PRODUCT', 'p2', 1000, s)).toBeUndefined();
    rememberPendingSignature(pending, s);
    expect(takePendingSignature('PRODUCT', 'p1', 1001 + MAX_AGE_MS, s)).toBeUndefined();
  });
});
