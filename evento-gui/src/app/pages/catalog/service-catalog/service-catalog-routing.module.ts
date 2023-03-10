import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { ServiceCatalogPage } from './service-catalog.page';

const routes: Routes = [
  {
    path: '',
    component: ServiceCatalogPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ServiceCatalogPageRoutingModule {}
