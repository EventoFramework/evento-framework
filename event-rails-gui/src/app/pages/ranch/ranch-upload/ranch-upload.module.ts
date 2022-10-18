import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { RanchUploadPageRoutingModule } from './ranch-upload-routing.module';

import { RanchUploadPage } from './ranch-upload.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    RanchUploadPageRoutingModule
  ],
  declarations: [RanchUploadPage]
})
export class RanchUploadPageModule {}
