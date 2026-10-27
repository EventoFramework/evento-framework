import {Component, ChangeDetectionStrategy, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {AuthService} from '../../services/auth.service';

@Component({
    selector: 'app-login',
    templateUrl: './login.page.html',
    styleUrls: ['./login.page.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class LoginPage implements OnInit {

  username = '';
  password = '';
  loading = false;
  error = false;

  constructor(readonly auth: AuthService,
              private router: Router,
              private route: ActivatedRoute) {
  }

  async ngOnInit() {
    await this.auth.ready();
    // OIDC: a session may already exist (back from the identity provider, another tab).
    if (this.auth.authenticated()) {
      this.router.navigateByUrl(this.redirectTarget());
    }
  }

  /** OIDC: the server takes the browser to the identity provider and back to the console. */
  signInWithProvider() {
    window.location.assign(this.auth.loginUrl);
  }

  private redirectTarget(): string {
    const redirect = this.route.snapshot.queryParamMap.get('redirect');
    // Only follow app-internal redirects; anything absolute could bounce off-site.
    return redirect && redirect.startsWith('/') ? redirect : '/dashboard';
  }

  async submit() {
    if (this.loading || !this.username || !this.password) {
      return;
    }
    this.loading = true;
    this.error = false;
    try {
      if (await this.auth.login(this.username, this.password)) {
        this.router.navigateByUrl(this.redirectTarget());
      } else {
        this.error = true;
      }
    } catch {
      this.error = true;
    } finally {
      this.loading = false;
    }
  }
}
