import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { MessageCatalogPage } from './message-catalog.page';

const routes: Routes = [
  {
    path: '',
    component: MessageCatalogPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class MessageCatalogPageRoutingModule {}
