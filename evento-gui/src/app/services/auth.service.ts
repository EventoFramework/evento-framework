import {Injectable, signal} from '@angular/core';
import {Router} from '@angular/router';
import {environment} from '../../environments/environment';
import {clearBasicAuth, encodeBasicAuth, getBasicAuth, setBasicAuth} from './api';

/**
 * Basic-auth session state.
 *
 * login() verifies the credentials against a protected endpoint before storing them —
 * Basic auth has no login handshake of its own, so a probe is the only way to fail fast
 * with a meaningful error instead of on the first data fetch.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  readonly authenticated = signal<boolean>(!!getBasicAuth());

  constructor(private router: Router) {
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

  logout(): void {
    clearBasicAuth();
    this.authenticated.set(false);
    this.router.navigateByUrl('/login');
  }
}
