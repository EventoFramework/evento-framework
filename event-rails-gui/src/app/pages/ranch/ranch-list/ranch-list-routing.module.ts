import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { RanchListPage } from './ranch-list.page';

const routes: Routes = [
  {
    path: '',
    component: RanchListPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class RanchListPageRoutingModule {}
