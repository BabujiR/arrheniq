import { describe, expect, it } from 'vitest';
import { fromRows, parseValue, summary, toRows } from './attributes';

describe('list-value attributes', () => {
  it('keeps numbers and booleans typed', () => {
    expect(parseValue('25')).toBe(25);
    expect(parseValue(' -20.5 ')).toBe(-20.5);
    expect(parseValue('true')).toBe(true);
    expect(parseValue('LONG_TERM')).toBe('LONG_TERM');
    expect(parseValue('25 °C')).toBe('25 °C');
  });

  it('round-trips rows and drops blank ones', () => {
    const attributes = { temp_c: 25, rh: 60, role: 'LONG_TERM' };
    const rows = [...toRows(attributes), { key: '', value: '' }];
    expect(fromRows(rows)).toEqual({ attributes });
  });

  it('reports invalid and repeated names', () => {
    expect(fromRows([{ key: 'Temp C', value: '25' }])).toHaveProperty('error');
    expect(fromRows([{ key: 'rh', value: '60' }, { key: 'rh', value: '65' }])).toHaveProperty('error');
  });

  it('summarises for the grid', () => {
    expect(summary({ temp_c: 25, rh: 60 })).toBe('temp_c 25 · rh 60');
  });
});
