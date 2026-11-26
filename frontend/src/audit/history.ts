import { label } from '../components/format';

const ACTIONS: Record<string, string> = {
  CREATE: 'Created',
  UPDATE: 'Changed',
  SUBMIT: 'Submitted for approval',
  APPROVE: 'Approved',
  REJECT: 'Rejected',
  RETIRE: 'Retired',
  LOGICAL_DELETE: 'Discarded',
  SIGN: 'Signed',
  SIGNATURE_SUPERSEDE: 'Signature superseded',
};

/** Plain-language name of an audit action. */
export function actionLabel(action: string): string {
  return ACTIONS[action] ?? label(action);
}

/** A stored value as people read it: empty for null, Yes/No, lists joined. */
export function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  if (typeof value === 'boolean') {
    return value ? 'Yes' : 'No';
  }
  if (Array.isArray(value)) {
    return value.length === 0 ? '—' : value.map(formatValue).join(', ');
  }
  if (typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

/**
 * Field names as stored (`status`, `test.ASSAY.upperLimit`, `tp.3M.unitsPlanned`, `grid.KF`) in words:
 * "Status", "ASSAY · upper limit", "3M · units planned", "KF · time points".
 */
export function fieldLabel(field: string): string {
  const words = (s: string) => s.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase();
  const parts = field.split('.');
  if (parts.length === 3 && (parts[0] === 'test' || parts[0] === 'tp')) {
    return `${parts[1]} · ${words(parts[2]!)}`;
  }
  if (parts.length === 2 && parts[0] === 'grid') {
    return `${parts[1]} · time points`;
  }
  const w = words(field);
  return w.charAt(0).toUpperCase() + w.slice(1);
}
