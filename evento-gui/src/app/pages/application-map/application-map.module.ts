import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ApplicationMapPageRoutingModule } from './application-map-routing.module';

import { ApplicationMapPage } from './application-map.page';
import {TranslateModule} from "@ngx-translate/core";
import {ComponentsModule} from "../../components/components.module";

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        IonicModule,
        ApplicationMapPageRoutingModule,
        TranslateModule,
        ComponentsModule
    ],
    declarations: [ApplicationMapPage]
})
export class ApplicationMapPageModule {}
