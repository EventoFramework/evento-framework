import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { PayloadInfoPage } from './payload-info.page';

const routes: Routes = [
  {
    path: '',
    component: PayloadInfoPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class PayloadInfoPageRoutingModule {}
