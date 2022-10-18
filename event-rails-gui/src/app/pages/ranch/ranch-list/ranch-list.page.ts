import {Component, OnInit} from '@angular/core';
import {RanchService} from "../../../services/ranch.service";

@Component({
  selector: 'app-ranch-list',
  templateUrl: './ranch-list.page.html',
  styleUrls: ['./ranch-list.page.scss'],
})
export class RanchListPage implements OnInit {
  elements: any[] = [];

  constructor(private ranchService: RanchService) {
  }

  async ngOnInit() {
    this.elements = await this.ranchService.findAll();
    console.log(this.elements)
  }

  async unregister(ranch: any) {
    await this.ranchService.unregister(ranch.name);
    console.log(this.elements)
    this.elements = this.elements.filter(r => r.name != ranch.name)
    console.log(this.elements)
  }
}
