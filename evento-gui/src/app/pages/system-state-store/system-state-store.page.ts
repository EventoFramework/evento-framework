import { Component, OnInit } from '@angular/core';
import {SystemStateStoreService} from "../../services/system-state-store.service";

@Component({
  selector: 'app-system-state-store',
  templateUrl: './system-state-store.page.html',
  styleUrls: ['./system-state-store.page.scss'],
})
export class SystemStateStorePage implements OnInit {

  events?
  private page: number;

  constructor(private service: SystemStateStoreService) { }

  ngOnInit() {
    return this.refresh()
  }

  async refresh() {
    this.page = 0;
    this.events = (await this.service.searchEvents()).content;
  }
}
