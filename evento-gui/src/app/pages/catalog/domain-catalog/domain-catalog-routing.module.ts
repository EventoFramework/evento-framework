import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { DomainCatalogPage } from './domain-catalog.page';

const routes: Routes = [
  {
    path: '',
    component: DomainCatalogPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class DomainCatalogPageRoutingModule {}
