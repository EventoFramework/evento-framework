import {Injectable, signal} from '@angular/core';
import {Router} from '@angular/router';
import {environment} from '../../environments/environment';
import {AuthMode, authHeaders, clearBasicAuth, encodeBasicAuth, getBasicAuth, setAuthMode, setBasicAuth} from './api';

/**
 * Session state, in the mode the server chose (`GET /api/auth/mode`).
 *
 * - `basic`: login() verifies the credentials against a protected endpoint before storing them —
 *   Basic auth has no login handshake of its own, so a probe is the only way to fail fast with a
 *   meaningful error instead of on the first data fetch.
 * - `oidc`: the server runs the sign-in with the identity provider ({@link #loginUrl}); the
 *   session is a cookie, so being signed in is asked to the server (`GET /api/auth/me`).
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  readonly authenticated = signal<boolean>(!!getBasicAuth());
  readonly mode = signal<AuthMode>('basic');
  private loginPath = '';
  private readyPromise?: Promise<void>;

  constructor(private router: Router) {
  }

  /** Learns the mode once; in OIDC mode, also whether the session is signed in. */
  ready(): Promise<void> {
    if (!this.readyPromise) {
      this.readyPromise = this.loadMode();
    }
    return this.readyPromise;
  }

  private async loadMode(): Promise<void> {
    try {
      const res = await fetch(environment.eventoServerUrl + '/api/auth/mode', {credentials: 'same-origin'});
      if (!res.ok) {
        return; // a server before 3.0: Basic
      }
      const body = await res.json();
      if (body.mode !== 'oidc') {
        return;
      }
      setAuthMode('oidc');
      this.mode.set('oidc');
      this.loginPath = body.loginUrl;
      const me = await fetch(environment.eventoServerUrl + '/api/auth/me', {credentials: 'same-origin'});
      this.authenticated.set(me.ok);
    } catch {
      // Unreachable server: keep Basic, the login page will show the error on submit.
    }
  }

  /** OIDC: where the browser goes to sign in with the identity provider. */
  get loginUrl(): string {
    return environment.eventoServerUrl + this.loginPath;
  }

  async login(username: string, password: string): Promise<boolean> {
    const encoded = encodeBasicAuth(username, password);
    // Raw fetch on purpose: apiFetch would treat the 401 of a wrong password as an
    // expired session and hard-redirect while we want to show an inline error.
    const res = await fetch(environment.eventoServerUrl + '/api/dashboard',
      {headers: {Authorization: 'Basic ' + encoded}});
    if (!res.ok) {
      return false;
    }
    setBasicAuth(encoded);
    this.authenticated.set(true);
    return true;
  }

  async logout(): Promise<void> {
    if (this.mode() === 'oidc') {
      const init: RequestInit = {method: 'POST', credentials: 'same-origin'};
      await fetch(environment.eventoServerUrl + '/api/auth/logout', {...init, headers: authHeaders(init)})
        .catch(() => undefined);
    }
    clearBasicAuth();
    this.authenticated.set(false);
    this.router.navigateByUrl('/login');
  }
}
