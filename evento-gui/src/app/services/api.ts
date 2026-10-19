import {environment} from '../../environments/environment';

/**
 * Centralized API access.
 *
 * Two ways to authenticate, chosen by the server (`GET /api/auth/mode`):
 *
 * - `basic` (default): the stored credential goes in an `Authorization: Basic …` header.
 * - `oidc`: the server signed the user in with the identity provider and keeps the session in an
 *   HttpOnly cookie the browser sends by itself; writes carry the CSRF token of the `XSRF-TOKEN`
 *   cookie in an `X-XSRF-TOKEN` header. No credential or token is stored here.
 *
 * On 401 the stored credential is dropped and the app navigates to /login (hard redirect on
 * purpose: it also aborts any in-flight polling). A 403 is a signed-in user without the role an
 * action needs (ADMIN): the caller reports it, the session stays (see `apiWrite`).
 */

export type AuthMode = 'basic' | 'oidc';

const STORAGE_KEY = 'evento_basic_auth';

let authMode: AuthMode = 'basic';

export function getAuthMode(): AuthMode {
  return authMode;
}

export function setAuthMode(mode: AuthMode): void {
  authMode = mode;
}

export function getBasicAuth(): string | null {
  return localStorage.getItem(STORAGE_KEY);
}

export function setBasicAuth(encoded: string): void {
  localStorage.setItem(STORAGE_KEY, encoded);
}

export function clearBasicAuth(): void {
  localStorage.removeItem(STORAGE_KEY);
}

export function encodeBasicAuth(username: string, password: string): string {
  // btoa is fine here: credentials are expected to be ASCII; escape non-latin just in case.
  return btoa(unescape(encodeURIComponent(username + ':' + password)));
}

/** The CSRF token the server put in the `XSRF-TOKEN` cookie (OIDC mode). */
export function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function onUnauthorized(): void {
  clearBasicAuth();
  if (!window.location.pathname.startsWith('/login')) {
    window.location.assign('/login');
  }
}

/** The request headers for the current auth mode. */
export function authHeaders(init?: RequestInit): Headers {
  const headers = new Headers(init?.headers);
  if (authMode === 'oidc') {
    const method = (init?.method ?? 'GET').toUpperCase();
    const token = csrfToken();
    if (token && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      headers.set('X-XSRF-TOKEN', token);
    }
  } else {
    const auth = getBasicAuth();
    if (auth) {
      headers.set('Authorization', 'Basic ' + auth);
    }
  }
  return headers;
}

/**
 * Fetch an API resource with the current credential attached.
 *
 * @param path path relative to the server root (e.g. `/api/dashboard`)
 * @param init standard fetch init
 */
export function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  return fetch(environment.eventoServerUrl + path, {...init, headers: authHeaders(init), credentials: 'same-origin'})
    .then(res => {
      if (res.status === 401) {
        onUnauthorized();
      }
      return res;
    });
}

/** Thrown by `apiWrite` when the server refuses or fails an action. */
export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

/**
 * A state-changing call (the console's ADMIN actions): resolves to the JSON body, or rejects with
 * an `ApiError` whose message can be shown to the user as it is.
 */
export async function apiWrite(path: string, init: RequestInit): Promise<any> {
  const res = await apiFetch(path, init);
  if (res.ok) {
    return res.status === 204 || res.headers.get('content-length') === '0' ? null : res.json();
  }
  if (res.status === 403) {
    throw new ApiError(403, 'This action needs the ADMIN role.');
  }
  throw new ApiError(res.status, `The server could not complete the action (HTTP ${res.status}).`);
}
