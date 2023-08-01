import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';

import {IonicModule} from '@ionic/angular';


import {PayloadCatalogPage} from './payload-catalog-page.component';
import {TranslateModule} from '@ngx-translate/core';
import {PayloadCatalogPageRoutingModule} from './payload-catalog-routing.module';
import {ComponentsModule} from '../../../components/components.module';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    PayloadCatalogPageRoutingModule,
    TranslateModule,
    ComponentsModule
  ],
  declarations: [PayloadCatalogPage]
})
export class PayloadCatalogPageModule {}
