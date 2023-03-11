import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ComponentCatalogPageRoutingModule } from './component-catalog-routing.module';

import { ComponentCatalogPage } from './component-catalog.page';
import {TranslateModule} from "@ngx-translate/core";
import {ComponentsModule} from "../../../components/components.module";

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    ComponentCatalogPageRoutingModule,
    TranslateModule,
    ComponentsModule
  ],
  declarations: [ComponentCatalogPage]
})
export class ComponentCatalogPageModule {}
