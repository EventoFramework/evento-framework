import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {ComponentCatalogPage} from './component-catalog.page';

const routes: Routes = [
  {
    path: '',
    component: ComponentCatalogPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ComponentCatalogPageRoutingModule {}
