import { describe, expect, it } from 'vitest';
import { actionLabel, fieldLabel, formatValue } from './history';

describe('record history wording', () => {
  it('names actions', () => {
    expect(actionLabel('LOGICAL_DELETE')).toBe('Discarded');
    expect(actionLabel('SESSION_REVOKED')).toBe('Session Revoked');
  });

  it('formats values', () => {
    expect(formatValue(null)).toBe('—');
    expect(formatValue(true)).toBe('Yes');
    expect(formatValue([1, 2])).toBe('1, 2');
    expect(formatValue(0)).toBe('0');
  });

  it('turns stored field names into words', () => {
    expect(fieldLabel('status')).toBe('Status');
    expect(fieldLabel('reserveUnits')).toBe('Reserve units');
    expect(fieldLabel('test.ASSAY.upperLimit')).toBe('ASSAY · upper limit');
    expect(fieldLabel('tp.3M.unitsPlanned')).toBe('3M · units planned');
    expect(fieldLabel('grid.KF')).toBe('KF · time points');
  });
});
