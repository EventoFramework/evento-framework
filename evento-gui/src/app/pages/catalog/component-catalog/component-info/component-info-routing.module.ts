import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {ComponentInfoPage} from './component-info.page';

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
