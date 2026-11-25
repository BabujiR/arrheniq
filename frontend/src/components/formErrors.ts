import type { FieldError } from '../api/http';

const MESSAGES: Record<string, string> = {
  REQUIRED: 'Required',
  FORMAT: 'Invalid format',
  TOO_LONG: 'Too long',
  INVALID_VALUE: 'Invalid value',
  OUT_OF_RANGE: 'Out of range',
  NOT_ALLOWED: 'Not allowed here',
  ORDER: 'Lower limit is greater than upper limit',
  NEGATIVE: 'Must be 0 or greater',
  DUPLICATE: 'Already used',
  UNKNOWN: 'Unknown',
};

/** Server field errors → form errors keyed by field name (Mantine `form.setErrors`). */
export function toFormErrors(errors: FieldError[]): Record<string, string> {
  const result: Record<string, string> = {};
  for (const e of errors) {
    if (!(e.field in result)) {
      result[e.field] = e.message ?? MESSAGES[e.code] ?? e.code;
    }
  }
  return result;
}
