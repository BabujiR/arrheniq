import { describe, expect, it } from 'vitest';
import type { Me } from '../api/types';
import { can } from './permissions';

const me = { permissions: ['masterdata.view', 'product.manage'] } as unknown as Me;

describe('can', () => {
  it('checks the permission list from /api/v1/me', () => {
    expect(can(me, 'product.manage')).toBe(true);
    expect(can(me, 'product.approve')).toBe(false);
    expect(can(undefined, 'product.manage')).toBe(false);
  });
});
