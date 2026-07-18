import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {AuthService} from './auth.service';

/** Redirects unauthenticated users to /login, remembering the requested URL. */
export const authGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.authenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], {queryParams: {redirect: state.url}});
};
