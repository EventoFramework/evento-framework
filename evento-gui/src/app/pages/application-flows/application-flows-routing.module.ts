import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {ApplicationFlowsPage} from './application-flows-page.component';

const routes: Routes = [
  {
    path: '',
    component: ApplicationFlowsPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ApplicationFlowsPageRoutingModule {}
