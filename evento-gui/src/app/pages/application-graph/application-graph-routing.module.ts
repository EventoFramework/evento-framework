import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ApplicationGraphPage } from './application-graph-page.component';

const routes: Routes = [
  {
    path: '',
    component: ApplicationGraphPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ApplicationGraphPageRoutingModule {}
