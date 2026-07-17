import {Component, ChangeDetectionStrategy, inject} from '@angular/core';
import {ThemeService} from './services/theme.service';

@Component({
    selector: 'app-root',
    templateUrl: 'app.component.html',
    styleUrls: ['app.component.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class AppComponent {
  // Public so the header template can bind the light/dark toggle. Injecting it
  // here also initializes the service at app start (applies the persisted
  // choice and follows the OS preference).
  readonly theme = inject(ThemeService);

  selectedTab = '';
  constructor() {}
}
