import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, isReasonRequired, onAuthProblem, readCookie, toApiError } from './http';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

afterEach(() => {
  vi.unstubAllGlobals();
  onAuthProblem(() => {});
});

describe('readCookie', () => {
  it('finds a cookie among others and decodes it', () => {
    expect(readCookie('a=1; XSRF-TOKEN=abc%3D%3D; b=2', 'XSRF-TOKEN')).toBe('abc==');
    expect(readCookie('a=1', 'XSRF-TOKEN')).toBeUndefined();
  });
});

describe('toApiError', () => {
  it('reads code and field errors at the top level', async () => {
    const e = await toApiError(json(400, { title: 'Validation failed', code: 'ARQ-VAL-001',
      errors: [{ field: 'code', code: 'FORMAT' }] }));
    expect(e).toBeInstanceOf(ApiError);
    expect(e.status).toBe(400);
    expect(e.code).toBe('ARQ-VAL-001');
    expect(e.errors).toEqual([{ field: 'code', code: 'FORMAT' }]);
  });

  it('reads code nested under properties and survives a non-JSON body', async () => {
    expect((await toApiError(json(409, { title: 'x', properties: { code: 'ARQ-CONC-001' } }))).code).toBe('ARQ-CONC-001');
    expect((await toApiError(new Response('oops', { status: 502 }))).code).toBe('HTTP-502');
  });
});

describe('api', () => {
  it('sends the CSRF token on state-changing requests only', async () => {
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=tok-1' });
    const fetchMock = vi.fn(async () => json(200, { ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    await api('GET', '/api/v1/me');
    await api('POST', '/api/v1/products', { code: 'X' });

    const [, getInit] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    const [, postInit] = fetchMock.mock.calls[1] as unknown as [string, RequestInit];
    expect((getInit.headers as Record<string, string>)['X-XSRF-TOKEN']).toBeUndefined();
    expect((postInit.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('tok-1');
    expect(postInit.body).toBe('{"code":"X"}');
    expect(postInit.credentials).toBe('same-origin');
  });

  it('reports 401 codes to the auth listener and throws ApiError', async () => {
    vi.stubGlobal('document', { cookie: '' });
    vi.stubGlobal('fetch', vi.fn(async () => json(401, { title: 'Session locked', code: 'ARQ-AUTH-LOCKED' })));
    const seen: string[] = [];
    onAuthProblem((code) => seen.push(code));
    await expect(api('GET', '/api/v1/me')).rejects.toMatchObject({ status: 401, code: 'ARQ-AUTH-LOCKED' });
    expect(seen).toEqual(['ARQ-AUTH-LOCKED']);
  });

  it('recognises the reason-required problem', () => {
    expect(isReasonRequired(new ApiError(422, 'ARQ-AUDIT-010', 'reason'))).toBe(true);
    expect(isReasonRequired(new ApiError(409, 'ARQ-CONC-001', 'stale'))).toBe(false);
  });
});
