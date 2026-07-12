import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import {SystemStateStoreService} from "../../services/system-state-store.service";
import {ActivatedRoute} from "@angular/router";

@Component({
    selector: 'app-system-state-store',
    templateUrl: './system-state-store.page.html',
    styleUrls: ['./system-state-store.page.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class SystemStateStorePage {


}
