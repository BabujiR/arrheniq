/**
 * A search value as text. The router JSON-parses search values, so `?signing=1` arrives as the number 1 and
 * `?x=true` as a boolean; both are turned back into text. Anything else (objects, arrays) is ignored.
 */
export function str(v: unknown): string | undefined {
  if (typeof v === 'string') {
    return v;
  }
  return typeof v === 'number' || typeof v === 'boolean' ? String(v) : undefined;
}
