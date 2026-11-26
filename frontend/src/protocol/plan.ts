import type { ProtocolContent, TimePoint, TimeUnit } from '../api/types';

/** Editing helpers for a protocol draft (time points, test × time-point grid, planned units). Pure functions. */

const DAYS: Record<TimeUnit, number> = { DAY: 1, WEEK: 7, MONTH: 30.4375 };

export function defaultLabel(value: number, unit: TimeUnit): string {
  return value === 0 ? 'Initial' : `${value}${unit[0]}`;
}

export function timepointKey(t: Pick<TimePoint, 'value' | 'unit'>): string {
  return `${t.value}${t.unit[0]}`;
}

/** Adds a time point in chronological order (the grid follows); ignores a duplicate. */
export function addTimepoint(c: ProtocolContent, value: number, unit: TimeUnit): ProtocolContent {
  if (c.timepoints.some((t) => t.value === value && t.unit === unit)) {
    return c;
  }
  const tp: TimePoint = { value, unit, label: defaultLabel(value, unit), unitsPlanned: 0 };
  const days = value * DAYS[unit];
  let at = c.timepoints.findIndex((t) => t.value * DAYS[t.unit] > days);
  if (at < 0) at = c.timepoints.length;
  const timepoints = [...c.timepoints.slice(0, at), tp, ...c.timepoints.slice(at)];
  // every test runs at a new time point until it is cleared (the usual case: the full panel at each pull)
  const tests = c.tests.map((t) => ({ ...t, at: [...t.at.map((i) => (i >= at ? i + 1 : i)), at].sort((a, b) => a - b) }));
  return { ...c, timepoints, tests };
}

/** "30 bottles in total", "1 bottle in total", "12 units in total". */
export function totalLabel(total: number, unitOfInventory: string | null): string {
  const unit = unitOfInventory?.trim().toLowerCase() || 'unit';
  const plural = total === 1 || unit.endsWith('s') ? unit : `${unit}s`;
  return `${total} ${plural} in total`;
}

/** Removes a time point and every grid cell at it. */
export function removeTimepoint(c: ProtocolContent, index: number): ProtocolContent {
  return {
    ...c,
    timepoints: c.timepoints.filter((_, i) => i !== index),
    tests: c.tests.map((t) => ({ ...t, at: t.at.filter((i) => i !== index).map((i) => (i > index ? i - 1 : i)) })),
  };
}

export function toggleCell(c: ProtocolContent, testIndex: number, tpIndex: number): ProtocolContent {
  return {
    ...c,
    tests: c.tests.map((t, i) =>
      i !== testIndex
        ? t
        : { ...t, at: t.at.includes(tpIndex) ? t.at.filter((x) => x !== tpIndex) : [...t.at, tpIndex].sort((a, b) => a - b) },
    ),
  };
}

/** Ticks a whole column when it isn't full, otherwise clears it. */
export function toggleColumn(c: ProtocolContent, tpIndex: number): ProtocolContent {
  const full = c.tests.every((t) => t.at.includes(tpIndex));
  return {
    ...c,
    tests: c.tests.map((t) => ({
      ...t,
      at: full ? t.at.filter((x) => x !== tpIndex) : [...new Set([...t.at, tpIndex])].sort((a, b) => a - b),
    })),
  };
}

export function testsAt(c: ProtocolContent, tpIndex: number): number {
  return c.tests.filter((t) => t.at.includes(tpIndex)).length;
}

export function totalUnits(c: ProtocolContent): number {
  return c.timepoints.reduce((sum, t) => sum + (t.unitsPlanned || 0), 0) + (c.reserveUnits || 0);
}

/** Same checks as the server (ProtocolService.warnings), for live feedback while editing. */
export function planWarnings(c: ProtocolContent): string[] {
  const out: string[] = [];
  c.timepoints.forEach((t, i) => {
    const n = testsAt(c, i);
    if (n > 0 && !t.unitsPlanned) {
      out.push(`No units planned at ${t.label}, although ${n} ${n === 1 ? 'test runs' : 'tests run'} there`);
    }
    if (n === 0) {
      out.push(`No test runs at ${t.label}`);
    }
  });
  return out;
}
