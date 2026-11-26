import { describe, expect, it } from 'vitest';
import { formatInstant, label } from './format';

describe('formatInstant', () => {
  it('shows the site time with its zone and UTC', () => {
    expect(formatInstant('2026-11-25T15:04:00Z', 'America/New_York')).toBe('Nov 25, 2026, 10:04 AM EST (2026-11-25 15:04 UTC)');
  });

  it('works without a site time zone', () => {
    expect(formatInstant('2026-11-25T15:04:00Z')).toMatch(/2026.*\(2026-11-25 15:04 UTC\)$/);
  });
});

describe('label', () => {
  it('turns codes into words', () => {
    expect(label('IN_REVIEW')).toBe('In Review');
  });
});
