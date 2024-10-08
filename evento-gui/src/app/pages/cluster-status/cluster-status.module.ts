import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';

import {IonicModule} from '@ionic/angular';

import {ClusterStatusPageRoutingModule} from './cluster-status-routing.module';

import {ClusterStatusPage} from './cluster-status.page';
import {TranslateModule} from '@ngx-translate/core';
import {ComponentsModule} from '../../components/components.module';
import {ConsumersComponent} from "./components/consumers/consumers.component";

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        IonicModule,
        ClusterStatusPageRoutingModule,
        TranslateModule,
        ComponentsModule,
        ConsumersComponent
    ],
  declarations: [ClusterStatusPage]
})
export class ClusterStatusPageModule {}
