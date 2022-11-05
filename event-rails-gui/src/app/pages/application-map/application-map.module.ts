import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ApplicationMapPageRoutingModule } from './application-map-routing.module';

import { ApplicationMapPage } from './application-map.page';
import {AppMapDiagramComponent} from "../../components/app-map-diagram/app-map-diagram.component";

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        IonicModule,
        ApplicationMapPageRoutingModule
    ],
    declarations: [ApplicationMapPage, AppMapDiagramComponent]
})
export class ApplicationMapPageModule {}
