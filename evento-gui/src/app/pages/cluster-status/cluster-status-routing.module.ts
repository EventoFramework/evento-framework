import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {ClusterStatusPage} from './cluster-status.page';

const routes: Routes = [
  {
    path: '',
    component: ClusterStatusPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ClusterStatusPageRoutingModule {}
