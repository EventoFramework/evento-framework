import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ServiceCatalogPageRoutingModule } from './service-catalog-routing.module';

import { ServiceCatalogPage } from './service-catalog.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    ServiceCatalogPageRoutingModule
  ],
  declarations: [ServiceCatalogPage]
})
export class ServiceCatalogPageModule {}
