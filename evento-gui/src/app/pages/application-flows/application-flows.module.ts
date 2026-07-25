import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';

import {IonicModule} from '@ionic/angular';

import {ApplicationFlowsPageRoutingModule} from './application-flows-routing.module';

import {ApplicationFlowsPage} from './application-flows-page.component';
import {TranslatePipe, TranslateDirective} from '@ngx-translate/core';

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        IonicModule,
        ApplicationFlowsPageRoutingModule,
        TranslatePipe, TranslateDirective
    ],
  declarations: [ApplicationFlowsPage]
})
export class ApplicationFlowsPageModule {}
