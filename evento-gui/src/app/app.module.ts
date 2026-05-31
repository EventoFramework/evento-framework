import {NgModule} from '@angular/core';
import {BrowserModule} from '@angular/platform-browser';
import {RouteReuseStrategy} from '@angular/router';

import {IonicModule, IonicRouteStrategy} from '@ionic/angular';

import {AppComponent} from './app.component';
import {AppRoutingModule} from './app-routing.module';
import {TranslateModule} from '@ngx-translate/core';
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
    TranslateModule.forRoot({
      defaultLanguage: 'en',
      loader: provideTranslateHttpLoader({prefix: './assets/i18n/', suffix: '.json'}),
    }),
    AppRoutingModule, PayloadCatalogPageModule, ComponentsModule],
  providers: [{provide: RouteReuseStrategy, useClass: IonicRouteStrategy}

  ],

  bootstrap: [AppComponent],
})
export class AppModule {
}
