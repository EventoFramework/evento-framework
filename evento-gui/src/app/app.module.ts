import {NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {RouteReuseStrategy} from '@angular/router';

import {IonicModule, IonicRouteStrategy} from '@ionic/angular';

import {AppComponent} from './app.component';
import {AppRoutingModule} from './app-routing.module';
import {provideTranslateService, TranslateDirective, TranslatePipe} from '@ngx-translate/core';
import {HttpClientModule} from '@angular/common/http';
import {provideTranslateHttpLoader} from '@ngx-translate/http-loader';
import {MarkdownModule} from 'ngx-markdown';
import {PayloadCatalogPageModule} from './pages/catalog/payload-catalog/payload-catalog.module';
import {ComponentsModule} from './components/components.module';

@NgModule({
  declarations: [AppComponent],
  imports: [BrowserModule, IonicModule.forRoot({mode: "md"}),
    HttpClientModule,
    MarkdownModule.forRoot(),
    // AppComponent's own template uses both the `| translate` pipe and the
    // `translate="..."` directive; under v17 it inherited them from
    // TranslateModule.forRoot() sitting in this array.
    TranslatePipe, TranslateDirective,
    AppRoutingModule, PayloadCatalogPageModule, ComponentsModule],
  providers: [{provide: RouteReuseStrategy, useClass: IonicRouteStrategy},
    // v18 removed TranslateModule: the root service is configured through a
    // provider instead of an imported module. The pipe and directive are now
    // standalone and imported per-module/component where the templates use them.
    provideTranslateService({
      lang: 'en',
      fallbackLang: 'en',
      loader: provideTranslateHttpLoader({prefix: './assets/i18n/', suffix: '.json'}),
    }),
  ],

  bootstrap: [AppComponent],
})
export class AppModule {
}
