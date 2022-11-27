import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ApplicationPetriNetPageRoutingModule } from './application-petri-net-routing.module';

import { ApplicationPetriNetPage } from './application-petri-net.page';
import {TranslateModule} from "@ngx-translate/core";

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        IonicModule,
        ApplicationPetriNetPageRoutingModule,
        TranslateModule
    ],
  declarations: [ApplicationPetriNetPage]
})
export class ApplicationPetriNetPageModule {}
