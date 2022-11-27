import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ClusterStatusPageRoutingModule } from './cluster-status-routing.module';

import { ClusterStatusPage } from './cluster-status.page';
import {TranslateModule} from "@ngx-translate/core";

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        IonicModule,
        ClusterStatusPageRoutingModule,
        TranslateModule
    ],
  declarations: [ClusterStatusPage]
})
export class ClusterStatusPageModule {}
