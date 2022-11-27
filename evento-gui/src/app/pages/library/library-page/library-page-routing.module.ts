import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { LibraryPagePage } from './library-page.page';

const routes: Routes = [
  {
    path: '',
    component: LibraryPagePage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class LibraryPagePageRoutingModule {}
