import { describe, expect, it } from 'vitest';
import type { SpecTest } from '../api/types';
import { alertText, compareVersions, limitText } from './limits';

const assay: SpecTest = {
  testMethodId: 'a',
  testCode: 'ASSAY',
  testStatus: 'ACTIVE',
  sortOrder: 10,
  displayName: 'Assay',
  resultsFormat: 'NUMERIC',
  unitCode: '%LC',
  replicates: 2,
  decimals: 1,
  lowerLimit: 90,
  upperLimit: 110,
  textLimit: null,
  alertLower: 92,
  alertUpper: null,
  trendable: true,
};

describe('limitText', () => {
  it('writes two-sided, one-sided, text and report-only criteria', () => {
    expect(limitText(assay)).toBe('90 – 110 %LC');
    expect(limitText({ ...assay, upperLimit: null, unitCode: '%' })).toBe('NLT 90 %');
    expect(limitText({ ...assay, lowerLimit: null, upperLimit: 0.5, unitCode: '%' })).toBe('NMT 0.5 %');
    expect(limitText({ ...assay, lowerLimit: null, upperLimit: null })).toBe('Report');
    expect(limitText({ ...assay, resultsFormat: 'TEXT', textLimit: 'Conforms' })).toBe('Conforms');
    expect(alertText(assay)).toBe('≥ 92');
  });
});

describe('compareVersions', () => {
  it('finds added, removed and changed tests', () => {
    const dissolution: SpecTest = { ...assay, testMethodId: 'd', testCode: 'DISS', lowerLimit: 80, upperLimit: null };
    const water: SpecTest = { ...assay, testMethodId: 'w', testCode: 'KF', lowerLimit: null, upperLimit: 0.5 };
    const changes = compareVersions([assay, dissolution], [{ ...assay, upperLimit: 105 }, water]);
    expect([...changes.added]).toEqual(['w']);
    expect(changes.removed.map((t) => t.testCode)).toEqual(['DISS']);
    expect([...(changes.changed.get('a') ?? [])]).toEqual(['upperLimit']);
  });
});
