import { describe, expect, it } from 'vitest';
import { formatDay, ruleText, rulesSummary, todayIso } from './pullDates';

describe('pull date wording', () => {
  it('shows a calendar date with its weekday, without time-zone shifts', () => {
    expect(formatDay('2025-02-28')).toBe('Fri 28 Feb 2025');
    expect(formatDay('2029-12-01')).toBe('Sat 1 Dec 2029');
  });

  it('explains a moved date', () => {
    expect(ruleText('SUNDAY_TO_PREVIOUS_WORKDAY')).toBe('Sunday → previous working day');
    expect(ruleText('HOLIDAY_TO_NEXT_WORKDAY')).toBe('Site holiday → next working day');
    expect(ruleText(null)).toBe('');
  });

  it('summarises the site rules', () => {
    expect(rulesSummary({ saturday: 'LEAVE', sunday: 'MOVE_BEFORE', holiday: 'MOVE_AFTER', holidays: ['2026-12-25'] })).toBe(
      'Saturday: kept · Sunday: previous working day · Site holidays (1): next working day',
    );
  });

  it('formats today', () => {
    expect(todayIso(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});
