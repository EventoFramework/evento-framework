import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';

import {IonicModule} from '@ionic/angular';

import {ComponentInfoPageRoutingModule} from './component-info-routing.module';

import {ComponentInfoPage} from './component-info.page';
import {ComponentsModule} from '../../../../components/components.module';
import {MarkdownModule} from 'ngx-markdown';
import {TranslateModule} from '@ngx-translate/core';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    ComponentInfoPageRoutingModule,
    ComponentsModule,
    MarkdownModule,
    TranslateModule
  ],
  declarations: [ComponentInfoPage]
})
export class ComponentInfoPageModule {}
