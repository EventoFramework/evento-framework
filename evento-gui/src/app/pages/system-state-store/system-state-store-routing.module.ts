import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { SystemStateStorePage } from './system-state-store.page';

const routes: Routes = [
  {
    path: '',
    component: SystemStateStorePage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SystemStateStorePageRoutingModule {}
