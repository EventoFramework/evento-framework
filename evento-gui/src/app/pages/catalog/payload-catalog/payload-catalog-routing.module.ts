import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { PayloadCatalogPage } from './payload-catalog-page.component';

const routes: Routes = [
  {
    path: '',
    component: PayloadCatalogPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class PayloadCatalogPageRoutingModule {}
