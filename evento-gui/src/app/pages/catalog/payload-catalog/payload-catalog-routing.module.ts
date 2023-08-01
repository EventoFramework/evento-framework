import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {PayloadCatalogPage} from './payload-catalog-page.component';

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
