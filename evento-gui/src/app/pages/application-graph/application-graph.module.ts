import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { ApplicationGraphPageRoutingModule } from './application-graph-routing.module';

import { ApplicationGraphPage } from './application-graph-page.component';
import {TranslateModule} from '@ngx-translate/core';
import {ComponentsModule} from '../../components/components.module';

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        IonicModule,
        ApplicationGraphPageRoutingModule,
        TranslateModule,
        ComponentsModule
    ],
    declarations: [ApplicationGraphPage]
})
export class ApplicationGraphPageModule {}
