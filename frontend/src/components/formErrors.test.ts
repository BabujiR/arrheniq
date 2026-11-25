import { describe, expect, it } from 'vitest';
import { toFormErrors } from './formErrors';

describe('toFormErrors', () => {
  it('maps server field errors to one message per field', () => {
    expect(
      toFormErrors([
        { field: 'code', code: 'FORMAT', message: '1-40 characters' },
        { field: 'code', code: 'DUPLICATE' },
        { field: 'name', code: 'REQUIRED' },
        { field: 'x', code: 'SOMETHING_NEW' },
      ]),
    ).toEqual({ code: '1-40 characters', name: 'Required', x: 'SOMETHING_NEW' });
  });
});
