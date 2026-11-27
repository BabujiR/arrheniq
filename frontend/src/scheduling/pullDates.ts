import type { DateRule } from '../api/scheduling';

/** A calendar date (yyyy-mm-dd) with its weekday, e.g. "Fri 28 Feb 2025". No time zone involved. */
export function formatDay(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(y!, m! - 1, d!));
  return new Intl.DateTimeFormat('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  })
    .format(date)
    .replace(',', '');
}

/** Today as yyyy-mm-dd in the browser's calendar. */
export function todayIso(now: Date = new Date()): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())}`;
}

const RULES: Record<DateRule, string> = {
  LEAVE: 'kept',
  MOVE_BEFORE: 'previous working day',
  MOVE_AFTER: 'next working day',
};

/** "Saturday: kept · Sunday: previous working day · Site holidays (3): next working day" */
export function rulesSummary(r: { saturday: DateRule; sunday: DateRule; holiday: DateRule; holidays: string[] }): string {
  return `Saturday: ${RULES[r.saturday]} · Sunday: ${RULES[r.sunday]} · Site holidays (${r.holidays.length}): ${RULES[r.holiday]}`;
}

/** Why a pull date differs from its calendar date: "Sunday → previous working day". */
export function ruleText(ruleApplied: string | null): string {
  if (!ruleApplied) {
    return '';
  }
  const [day, ...rest] = ruleApplied.split('_TO_');
  const what = day === 'HOLIDAY' ? 'Site holiday' : day!.charAt(0) + day!.slice(1).toLowerCase();
  const where = rest.join('_TO_') === 'PREVIOUS_WORKDAY' ? 'previous working day' : 'next working day';
  return `${what} → ${where}`;
}
