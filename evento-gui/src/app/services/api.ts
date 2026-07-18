import {environment} from '../../environments/environment';

/**
 * Centralized API access with HTTP Basic auth.
 *
 * Replaces the old window.fetch monkey-patch (prompt() + Bearer token). Services call
 * {@link apiFetch} with an API path; the stored credential is attached as an
 * `Authorization: Basic …` header. On 401/403 the credential is dropped and the app
 * navigates to /login (hard redirect on purpose: it also aborts any in-flight polling).
 */

const STORAGE_KEY = 'evento_basic_auth';

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

function onUnauthorized(): void {
  clearBasicAuth();
  if (!window.location.pathname.startsWith('/login')) {
    window.location.assign('/login');
  }
}

/**
 * Fetch an API resource with the Basic credential attached.
 *
 * @param path path relative to the server root (e.g. `/api/dashboard`)
 * @param init standard fetch init
 */
export function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers);
  const auth = getBasicAuth();
  if (auth) {
    headers.set('Authorization', 'Basic ' + auth);
  }
  return fetch(environment.eventoServerUrl + path, {...init, headers}).then(res => {
    if (res.status === 401 || res.status === 403) {
      onUnauthorized();
    }
    return res;
  });
}
