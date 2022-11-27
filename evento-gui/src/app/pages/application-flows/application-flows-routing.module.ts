import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ApplicationFlowsPage } from './application-flows-page.component';

const routes: Routes = [
  {
    path: '',
    component: ApplicationFlowsPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ApplicationFlowsPageRoutingModule {}
