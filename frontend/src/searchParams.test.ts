import { describe, expect, it } from 'vitest';
import { str } from './searchParams';

describe('str', () => {
  it('keeps text and turns parsed numbers and booleans back into text', () => {
    expect(str('b6431256-6ae8-4184-a7d2-f9507db739f9')).toBe('b6431256-6ae8-4184-a7d2-f9507db739f9');
    expect(str(1)).toBe('1'); // ?signing=1 after a full page load (return from re-authentication)
    expect(str(true)).toBe('true');
    expect(str({ a: 1 })).toBeUndefined();
    expect(str(undefined)).toBeUndefined();
  });
});
