import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';


import { PayloadCatalogPage } from './payload-catalog-page.component';
import {WrappedContentComponent} from "../../../components/wrapped-content/wrapped-content.component";
import {TranslateModule} from "@ngx-translate/core";
import {PayloadCatalogPageRoutingModule} from "./payload-catalog-routing.module";

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    PayloadCatalogPageRoutingModule,
    TranslateModule
  ],
  exports: [
    WrappedContentComponent
  ],
  declarations: [PayloadCatalogPage, WrappedContentComponent]
})
export class PayloadCatalogPageModule {}
