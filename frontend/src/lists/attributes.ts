import type { AttributeValue } from '../api/types';

/**
 * List-value attributes (e.g. temperature and humidity of a storage condition) edited as text rows.
 * The backend accepts text, numbers and true/false; keys are lower_snake_case.
 */
export interface AttributeRow {
  key: string;
  value: string;
}

export const ATTRIBUTE_KEY = /^[a-z][a-z0-9_]{0,39}$/;

export function toRows(attributes: Record<string, AttributeValue>): AttributeRow[] {
  return Object.entries(attributes).map(([key, value]) => ({ key, value: String(value) }));
}

/** Typed value: numbers and true/false become numbers and booleans, everything else stays text. */
export function parseValue(text: string): AttributeValue {
  const t = text.trim();
  if (/^-?\d+(\.\d+)?$/.test(t)) {
    return Number(t);
  }
  if (t === 'true' || t === 'false') {
    return t === 'true';
  }
  return t;
}

/** Rows to attributes; blank rows are dropped. Returns the first problem instead if a key is invalid or repeated. */
export function fromRows(rows: AttributeRow[]): { attributes: Record<string, AttributeValue> } | { error: string } {
  const attributes: Record<string, AttributeValue> = {};
  for (const row of rows) {
    const key = row.key.trim();
    if (!key && !row.value.trim()) {
      continue;
    }
    if (!ATTRIBUTE_KEY.test(key)) {
      return { error: `“${key || '(empty)'}” is not a valid name: use lower-case letters, digits and _ (e.g. temp_c)` };
    }
    if (key in attributes) {
      return { error: `“${key}” appears twice` };
    }
    attributes[key] = parseValue(row.value);
  }
  return { attributes };
}

/** Short display, e.g. "temp_c 25 · rh 60 · role LONG_TERM". */
export function summary(attributes: Record<string, AttributeValue>): string {
  return Object.entries(attributes)
    .map(([k, v]) => `${k} ${String(v)}`)
    .join(' · ');
}
