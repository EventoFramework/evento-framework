import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { LibraryPagePageRoutingModule } from './library-page-routing.module';

import { LibraryPagePage } from './library-page.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    LibraryPagePageRoutingModule
  ],
  declarations: [LibraryPagePage]
})
export class LibraryPagePageModule {}
