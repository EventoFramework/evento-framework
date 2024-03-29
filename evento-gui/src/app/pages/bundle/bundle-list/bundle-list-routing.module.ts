import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {BundleListPage} from './bundle-list.page';

const routes: Routes = [
  {
    path: '',
    component: BundleListPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class BundleListPageRoutingModule {}
