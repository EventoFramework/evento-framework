import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';

import {IonicModule} from '@ionic/angular';

import {BundleListPageRoutingModule} from './bundle-list-routing.module';

import {BundleListPage} from './bundle-list.page';
import {TranslateModule} from '@ngx-translate/core';
import {ComponentsModule} from '../../../components/components.module';

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        IonicModule,
        BundleListPageRoutingModule,
        TranslateModule,
        ComponentsModule
    ],
  declarations: [BundleListPage]
})
export class BundleListPageModule {}
