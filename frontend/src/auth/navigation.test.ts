import { describe, expect, it } from 'vitest';
import { reauthUrl } from './navigation';

describe('reauthUrl', () => {
  it('binds a signature re-authentication to the record version', () => {
    const url = reauthUrl('SIGN', '/products/p1?signing=1', { recordType: 'PRODUCT', recordId: 'p1', recordVersion: 3 });
    const params = new URL(url, 'http://x').searchParams;
    expect(url.startsWith('/auth/reauth?')).toBe(true);
    expect(params.get('purpose')).toBe('SIGN');
    expect(params.get('returnTo')).toBe('/products/p1?signing=1');
    expect(params.get('recordType')).toBe('PRODUCT');
    expect(params.get('recordId')).toBe('p1');
    expect(params.get('recordVersion')).toBe('3');
  });

  it('sends no record for an unlock', () => {
    const params = new URL(reauthUrl('UNLOCK', '/'), 'http://x').searchParams;
    expect(params.get('purpose')).toBe('UNLOCK');
    expect(params.has('recordId')).toBe(false);
  });
});
