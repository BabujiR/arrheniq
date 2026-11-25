/**
 * Fetch wrapper for the ArrheniQ API (same origin, session cookie). It
 * - sends the CSRF token (XSRF-TOKEN cookie → X-XSRF-TOKEN header) on every state-changing request,
 * - turns RFC 9457 problem responses into {@link ApiError} with the stable backend `code`,
 * - reports authentication problems (401) to one listener (the session provider: login or lock screen).
 */

export interface FieldError {
  field: string;
  code: string;
  message?: string | null;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly errors: FieldError[];

  constructor(status: number, code: string, title: string, errors: FieldError[] = []) {
    super(title);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}

type AuthProblemListener = (code: string) => void;
let authProblemListener: AuthProblemListener = () => {};

/** Registers the single handler for 401 responses (codes ARQ-AUTH-401 / -LOCKED / -EXPIRED / -REVOKED / -TENANT). */
export function onAuthProblem(listener: AuthProblemListener): void {
  authProblemListener = listener;
}

export function readCookie(cookieHeader: string, name: string): string | undefined {
  for (const part of cookieHeader.split(';')) {
    const [rawKey, ...rest] = part.trim().split('=');
    if (rawKey === name) {
      return decodeURIComponent(rest.join('='));
    }
  }
  return undefined;
}

export function csrfToken(): string | undefined {
  return readCookie(globalThis.document?.cookie ?? '', 'XSRF-TOKEN');
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

export async function api<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET') {
    const token = csrfToken();
    if (token) {
      headers['X-XSRF-TOKEN'] = token;
    }
  }
  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (response.ok) {
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }
  const error = await toApiError(response);
  if (response.status === 401) {
    authProblemListener(error.code);
  }
  throw error;
}

interface ProblemBody {
  title?: string;
  code?: string;
  errors?: FieldError[];
  properties?: { code?: string; errors?: FieldError[] };
}

/** Reads a problem response; `code`/`errors` may be top-level or under `properties` depending on serialisation. */
export async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemBody = {};
  try {
    problem = (await response.json()) as ProblemBody;
  } catch {
    problem = {};
  }
  const code = problem.code ?? problem.properties?.code ?? `HTTP-${response.status}`;
  const errors = problem.errors ?? problem.properties?.errors ?? [];
  const title = problem.title ?? (response.statusText || 'Request failed');
  return new ApiError(response.status, code, title, errors);
}

/** True if the error means "the server wants a reason for this change" (URS-AUD-004). */
export function isReasonRequired(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'ARQ-AUDIT-010';
}
