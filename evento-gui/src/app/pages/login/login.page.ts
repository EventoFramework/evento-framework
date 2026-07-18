import {Component, ChangeDetectionStrategy} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {AuthService} from '../../services/auth.service';

@Component({
    selector: 'app-login',
    templateUrl: './login.page.html',
    styleUrls: ['./login.page.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class LoginPage {

  username = '';
  password = '';
  loading = false;
  error = false;

  constructor(private auth: AuthService,
              private router: Router,
              private route: ActivatedRoute) {
  }

  async submit() {
    if (this.loading || !this.username || !this.password) {
      return;
    }
    this.loading = true;
    this.error = false;
    try {
      if (await this.auth.login(this.username, this.password)) {
        const redirect = this.route.snapshot.queryParamMap.get('redirect');
        // Only follow app-internal redirects; anything absolute could bounce off-site.
        this.router.navigateByUrl(redirect && redirect.startsWith('/') ? redirect : '/dashboard');
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
