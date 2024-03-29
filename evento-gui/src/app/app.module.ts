import {NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {RouteReuseStrategy} from '@angular/router';

import {IonicModule, IonicRouteStrategy} from '@ionic/angular';

import {AppComponent} from './app.component';
import {AppRoutingModule} from './app-routing.module';
import {TranslateLoader, TranslateModule} from '@ngx-translate/core';
import {HttpClient, HttpClientModule} from '@angular/common/http';
import {TranslateHttpLoader} from '@ngx-translate/http-loader';
import {MarkdownModule} from 'ngx-markdown';
import {PayloadCatalogPageModule} from './pages/catalog/payload-catalog/payload-catalog.module';
import {ComponentsModule} from './components/components.module';

export const createTranslateLoader = (http: HttpClient) =>
  new TranslateHttpLoader(http, './assets/i18n/', '.json');

@NgModule({
  declarations: [AppComponent],
  imports: [BrowserModule, IonicModule.forRoot({mode: "md"}),
    HttpClientModule,
    MarkdownModule.forRoot(),
    TranslateModule.forRoot({
      defaultLanguage: 'en',
      loader: {
        provide: TranslateLoader,
        useFactory: (createTranslateLoader),
        deps: [HttpClient],
      }
    }),
    AppRoutingModule, PayloadCatalogPageModule, ComponentsModule],
  providers: [{provide: RouteReuseStrategy, useClass: IonicRouteStrategy}

  ],

  bootstrap: [AppComponent],
})
export class AppModule {
}
