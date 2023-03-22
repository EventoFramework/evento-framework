import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { BundleInfoPageRoutingModule } from './bundle-info-routing.module';

import { BundleInfoPage } from './bundle-info.page';
import {TranslateModule} from '@ngx-translate/core';
import {ComponentsModule} from '../../../components/components.module';
import {MarkdownModule} from 'ngx-markdown';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    BundleInfoPageRoutingModule,
    TranslateModule,
    ComponentsModule,
    MarkdownModule
  ],
  declarations: [BundleInfoPage]
})
export class BundleInfoPageModule {}
