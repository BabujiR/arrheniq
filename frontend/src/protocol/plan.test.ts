import { describe, expect, it } from 'vitest';
import type { ProtocolContent } from '../api/types';
import { addTimepoint, planWarnings, removeTimepoint, toggleCell, toggleColumn, totalUnits } from './plan';

const base: ProtocolContent = {
  studyType: 'LONG_TERM',
  conditionCode: '25C60RH',
  reserveUnits: 10,
  fillUnitCode: 'BOTTLE',
  changeSummary: null,
  timepoints: [
    { value: 0, unit: 'MONTH', label: 'Initial', unitsPlanned: 4 },
    { value: 6, unit: 'MONTH', label: '6M', unitsPlanned: 4 },
  ],
  tests: [
    { testMethodId: 'a', testCode: 'ASSAY', testName: 'Assay', testStatus: 'ACTIVE', resultsFormat: 'NUMERIC', at: [0, 1] },
    { testMethodId: 'k', testCode: 'KF', testName: 'Water', testStatus: 'ACTIVE', resultsFormat: 'NUMERIC', at: [1] },
  ],
};

describe('protocol plan', () => {
  it('inserts time points chronologically and keeps the grid aligned', () => {
    const c = addTimepoint(base, 3, 'MONTH');
    expect(c.timepoints.map((t) => t.label)).toEqual(['Initial', '3M', '6M']);
    expect(c.tests[0]!.at).toEqual([0, 2]);
    expect(c.tests[1]!.at).toEqual([2]);
    expect(addTimepoint(c, 3, 'MONTH')).toBe(c);
    expect(addTimepoint(base, 2, 'WEEK').timepoints[1]!.label).toBe('2W');
  });

  it('removes a time point with its cells', () => {
    const c = removeTimepoint(base, 0);
    expect(c.timepoints.map((t) => t.label)).toEqual(['6M']);
    expect(c.tests[0]!.at).toEqual([0]);
  });

  it('toggles cells and columns', () => {
    expect(toggleCell(base, 1, 0).tests[1]!.at).toEqual([0, 1]);
    expect(toggleCell(base, 0, 0).tests[0]!.at).toEqual([1]);
    expect(toggleColumn(base, 0).tests.map((t) => t.at)).toEqual([[0, 1], [0, 1]]);
    expect(toggleColumn(base, 1).tests.map((t) => t.at)).toEqual([[0], []]);
  });

  it('totals units and warns about gaps', () => {
    expect(totalUnits(base)).toBe(18);
    const c = addTimepoint(base, 12, 'MONTH');
    expect(planWarnings(c)).toEqual(['No test runs at 12M']);
    const noUnits = toggleCell(c, 0, 2);
    expect(planWarnings(noUnits)).toEqual(['No units planned at 12M, although 1 test runs there']);
  });
});
