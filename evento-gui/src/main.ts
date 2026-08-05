import {enableProdMode, provideZoneChangeDetection} from '@angular/core';
import {platformBrowser} from '@angular/platform-browser';

import {AppModule} from './app/app.module';
import {environment} from './environments/environment';

if (environment.production) {
  enableProdMode();
}

// API authentication lives in services/api.ts (Basic auth via apiFetch) — the old
// global window.fetch proxy + prompt() token flow is gone.
platformBrowser().bootstrapModule(AppModule, { applicationProviders: [provideZoneChangeDetection()], })
  .catch(err => console.log(err));
