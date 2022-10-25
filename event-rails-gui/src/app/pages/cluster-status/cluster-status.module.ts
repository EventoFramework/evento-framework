import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ClusterStatusPageRoutingModule } from './cluster-status-routing.module';

import { ClusterStatusPage } from './cluster-status.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    ClusterStatusPageRoutingModule
  ],
  declarations: [ClusterStatusPage]
})
export class ClusterStatusPageModule {}
