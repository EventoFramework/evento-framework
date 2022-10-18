import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { RanchUploadPage } from './ranch-upload.page';

const routes: Routes = [
  {
    path: '',
    component: RanchUploadPage
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class RanchUploadPageRoutingModule {}
