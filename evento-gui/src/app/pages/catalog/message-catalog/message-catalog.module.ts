import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { MessageCatalogPageRoutingModule } from './message-catalog-routing.module';

import { MessageCatalogPage } from './message-catalog.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    MessageCatalogPageRoutingModule
  ],
  declarations: [MessageCatalogPage]
})
export class MessageCatalogPageModule {}
