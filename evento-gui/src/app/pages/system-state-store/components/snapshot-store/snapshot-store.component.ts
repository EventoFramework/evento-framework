import { Component, OnInit } from '@angular/core';
import {SystemStateStoreService} from "../../../../services/system-state-store.service";
import {ActivatedRoute, RouterLink} from "@angular/router";
import {DatePipe, JsonPipe, KeyValuePipe, NgForOf, NgIf} from "@angular/common";
import {IonicModule} from "@ionic/angular";
import {TranslateModule} from "@ngx-translate/core";
import {FormsModule} from "@angular/forms";

@Component({
  selector: 'app-snapshot-store',
  templateUrl: './snapshot-store.component.html',
  styleUrls: ['./snapshot-store.component.scss'],
  standalone: true,
  imports: [
    DatePipe,
    IonicModule,
    JsonPipe,
    KeyValuePipe,
    NgForOf,
    RouterLink,
    TranslateModule,
    NgIf,
    FormsModule
  ]
})
export class SnapshotStoreComponent  implements OnInit {

  snapshots?: any[]
  parameters: any = {
    page: 0
  }
  loading = false;

  constructor(private service: SystemStateStoreService,
              private route: ActivatedRoute) { }

  ngOnInit() {
    this.parameters = {...this.route.snapshot.queryParams};
    this.refresh();
  }


  async refresh() {
    try{
      this.loading = true;
      this.parameters.page = 0;
      this.snapshots = (await this.service.searchSnapshots(this.parameters)).content;
      this.updateQueryParams();
    }finally {
      this.loading = false;
    }
  }

  handleRefresh($event: any) {
    this.refresh().finally(() =>  $event.target.complete())
  }

  async onIonInfinite($event: any) {
    try {
      this.loading = true;
      this.parameters.page++;
      this.snapshots = this.snapshots.concat((await this.service.searchSnapshots(this.parameters)).content);
      this.updateQueryParams();
    }finally {
      this.loading = false;
      $event.target.complete()
    }
  }

  private updateQueryParams() {

    const searchParams = new URLSearchParams(window.location.search);
    for (let key of Object.keys(this.parameters)) {
      if (this.parameters[key]) {
        searchParams.set(key, this.parameters[key]);
      }else{
        searchParams.delete(key)
      }
    }
    history.pushState(null, '', window.location.pathname + '?' + searchParams.toString());
  }


}
