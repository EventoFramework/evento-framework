import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ApplicationMapPageRoutingModule } from './application-map-routing.module';

import { ApplicationMapPage } from './application-map.page';
import {EdgedCirclePackComponent} from "../../components/edged-circle-pack/edged-circle-pack.component";

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        IonicModule,
        ApplicationMapPageRoutingModule
    ],
    declarations: [ApplicationMapPage, EdgedCirclePackComponent]
})
export class ApplicationMapPageModule {}
