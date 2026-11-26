import type { SpecTest } from '../api/types';

type Limits = Pick<SpecTest, 'resultsFormat' | 'lowerLimit' | 'upperLimit' | 'textLimit' | 'unitCode'>;

/**
 * Acceptance criterion as written on a specification (URS-MD-003):
 * "90.0 – 110.0 %LC", "NLT 80 %", "NMT 0.5 %", a text limit such as "Conforms", or "Report" when there is none.
 */
export function limitText(t: Limits): string {
  if (t.resultsFormat === 'TEXT') {
    return t.textLimit?.trim() || 'Report';
  }
  const unit = t.unitCode ? ` ${t.unitCode}` : '';
  const lo = t.lowerLimit;
  const hi = t.upperLimit;
  if (lo != null && hi != null) {
    return `${lo} – ${hi}${unit}`;
  }
  if (lo != null) {
    return `NLT ${lo}${unit}`;
  }
  if (hi != null) {
    return `NMT ${hi}${unit}`;
  }
  return 'Report';
}

export function alertText(t: Pick<SpecTest, 'alertLower' | 'alertUpper'>): string {
  if (t.alertLower != null && t.alertUpper != null) {
    return `${t.alertLower} – ${t.alertUpper}`;
  }
  if (t.alertLower != null) {
    return `≥ ${t.alertLower}`;
  }
  if (t.alertUpper != null) {
    return `≤ ${t.alertUpper}`;
  }
  return '';
}

const COMPARED: (keyof SpecTest)[] = [
  'displayName', 'unitCode', 'replicates', 'decimals', 'lowerLimit', 'upperLimit', 'textLimit', 'alertLower',
  'alertUpper', 'trendable', 'sortOrder',
];

export interface VersionChanges {
  added: Set<string>;
  removed: SpecTest[];
  /** changed fields per test method id */
  changed: Map<string, Set<keyof SpecTest>>;
}

/** What changed from the previous version: tests added, removed, and changed fields (for highlighting). */
export function compareVersions(previous: SpecTest[], current: SpecTest[]): VersionChanges {
  const before = new Map(previous.map((t) => [t.testMethodId, t]));
  const after = new Map(current.map((t) => [t.testMethodId, t]));
  const changes: VersionChanges = { added: new Set(), removed: [], changed: new Map() };
  for (const t of current) {
    const old = before.get(t.testMethodId);
    if (!old) {
      changes.added.add(t.testMethodId);
      continue;
    }
    const fields = new Set(COMPARED.filter((f) => !same(old[f], t[f])));
    if (fields.size > 0) {
      changes.changed.set(t.testMethodId, fields);
    }
  }
  changes.removed = previous.filter((t) => !after.has(t.testMethodId));
  return changes;
}

function same(a: unknown, b: unknown): boolean {
  if (typeof a === 'number' && typeof b === 'number') {
    return a === b;
  }
  return (a ?? null) === (b ?? null);
}
