import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ComponentInfoPage } from './component-info.page';

const routes: Routes = [
  {
    path: '',
    component: ComponentInfoPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ComponentInfoPageRoutingModule {}
