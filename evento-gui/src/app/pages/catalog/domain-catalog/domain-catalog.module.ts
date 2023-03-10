import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { DomainCatalogPageRoutingModule } from './domain-catalog-routing.module';

import { DomainCatalogPage } from './domain-catalog.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    DomainCatalogPageRoutingModule
  ],
  declarations: [DomainCatalogPage]
})
export class DomainCatalogPageModule {}
