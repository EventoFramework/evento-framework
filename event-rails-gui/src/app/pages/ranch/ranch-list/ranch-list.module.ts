import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { RanchListPageRoutingModule } from './ranch-list-routing.module';

import { RanchListPage } from './ranch-list.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    RanchListPageRoutingModule
  ],
  declarations: [RanchListPage]
})
export class RanchListPageModule {}
