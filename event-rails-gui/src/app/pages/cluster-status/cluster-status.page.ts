import { Component, OnInit } from '@angular/core';
import {ClusterStatusService} from "../../services/cluster-status.service";

@Component({
  selector: 'app-cluster-status',
  templateUrl: './cluster-status.page.html',
  styleUrls: ['./cluster-status.page.scss'],
})
export class ClusterStatusPage implements OnInit {

  constructor(private clusterStatusService: ClusterStatusService) { }

  ngOnInit() {

    this.clusterStatusService.getAvailableView().subscribe(e => {
      console.log(e);
    })
  }

}
