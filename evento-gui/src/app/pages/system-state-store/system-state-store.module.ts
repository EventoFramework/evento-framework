import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { SystemStateStorePageRoutingModule } from './system-state-store-routing.module';

import { SystemStateStorePage } from './system-state-store.page';
import {ComponentsModule} from "../../components/components.module";
import {TranslateModule} from "@ngx-translate/core";
import {EventStoreComponent} from "./components/event-store/event-store.component";
import {SnapshotStoreComponent} from "./components/snapshot-store/snapshot-store.component";

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    SystemStateStorePageRoutingModule,
    ComponentsModule,
    TranslateModule,
    EventStoreComponent,
    SnapshotStoreComponent
  ],
  declarations: [SystemStateStorePage]
})
export class SystemStateStorePageModule {}
