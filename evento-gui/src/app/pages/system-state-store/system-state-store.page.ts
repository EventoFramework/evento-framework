import { Component, OnInit } from '@angular/core';
import {SystemStateStoreService} from "../../services/system-state-store.service";
import {ActivatedRoute} from "@angular/router";

@Component({
  selector: 'app-system-state-store',
  templateUrl: './system-state-store.page.html',
  styleUrls: ['./system-state-store.page.scss'],
})
export class SystemStateStorePage implements OnInit {

  events?: any[]
  parameters: any = {
    page: 0
  }
  loading = false;

  constructor(private service: SystemStateStoreService,
              private route: ActivatedRoute) { }

  ngOnInit() {

    // this.route.queryParamMap.subscribe(async q => this.refresh());
  }

  ionViewWillEnter(){
      this.parameters = {...this.route.snapshot.queryParams};
      this.refresh();
  }
  async refresh() {
    try{
      this.loading = true;
      this.parameters.page = 0;
      this.events = (await this.service.searchEvents(this.parameters)).content;
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
      this.events = this.events.concat((await this.service.searchEvents(this.parameters)).content);
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
