import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';

import {IonicModule} from '@ionic/angular';

import {PayloadInfoPageRoutingModule} from './payload-info-routing.module';

import {PayloadInfoPage} from './payload-info.page';
import {PayloadCatalogPageModule} from '../payload-catalog.module';
import {TranslateModule} from '@ngx-translate/core';
import {MarkdownModule} from 'ngx-markdown';
import {ComponentsModule} from '../../../../components/components.module';
import {RepositoryLogoPipe} from "../../../../pipes/repository-logo.pipe";

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        IonicModule,
        PayloadInfoPageRoutingModule,
        PayloadCatalogPageModule,
        TranslateModule,
        MarkdownModule,
        ComponentsModule,
        RepositoryLogoPipe
    ],
    declarations: [PayloadInfoPage]
})
export class PayloadInfoPageModule {}
