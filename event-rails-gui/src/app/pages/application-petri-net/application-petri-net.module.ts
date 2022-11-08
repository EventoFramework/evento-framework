import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ApplicationPetriNetPageRoutingModule } from './application-petri-net-routing.module';

import { ApplicationPetriNetPage } from './application-petri-net.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    ApplicationPetriNetPageRoutingModule
  ],
  declarations: [ApplicationPetriNetPage]
})
export class ApplicationPetriNetPageModule {}
