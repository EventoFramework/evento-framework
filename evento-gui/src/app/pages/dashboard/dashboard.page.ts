import {Component, OnInit} from '@angular/core';
import {DashboardService} from "../../services/dashboard.service";

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.page.html',
  styleUrls: ['./dashboard.page.scss'],
})
export class DashboardPage implements OnInit {
  dashboard: any;

  constructor(private dashboardService: DashboardService) {
  }

  ngOnInit() {
  }

  async ionViewWillEnter() {
    this.dashboard = await this.dashboardService.getDashboard();
    console.log(this.dashboard);
  }

}
