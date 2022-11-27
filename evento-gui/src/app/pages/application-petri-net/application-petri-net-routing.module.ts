import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ApplicationPetriNetPage } from './application-petri-net.page';

const routes: Routes = [
  {
    path: '',
    component: ApplicationPetriNetPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ApplicationPetriNetPageRoutingModule {}
