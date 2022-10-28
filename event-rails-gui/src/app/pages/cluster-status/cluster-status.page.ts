import {Component, OnDestroy, OnInit} from '@angular/core';
import {ClusterStatusService} from "../../services/cluster-status.service";
import {Subscription} from "rxjs";

@Component({
  selector: 'app-cluster-status',
  templateUrl: './cluster-status.page.html',
  styleUrls: ['./cluster-status.page.scss'],
})
export class ClusterStatusPage implements OnInit, OnDestroy {

  public view = {}
  public attendedView = [];
  public externalView = [];
  private viewSubscription: Subscription;
  private availableViewSubscription: Subscription;

  constructor(private clusterStatusService: ClusterStatusService) {
  }

  async ngOnInit() {

    const attendedView = await this.clusterStatusService.getAttendedView();
    for (let node of attendedView) {
      this.view[node] = {
        isOnline: false,
        isAvailable: false,
        replicas: {},
        replicaCount: 0
      }
    }
    this.attendedView = attendedView;

    this.viewSubscription = this.clusterStatusService.getView().subscribe(view => {
      console.log("VIEW", view)
      const upNodes = view.map(n => n.nodeName);
      this.externalView = upNodes.filter(n => !this.attendedView.includes(n))
      for (let node of attendedView) {
        this.view[node].isOnline = upNodes.includes(node);
        this.view[node].replicas = view.filter(n => n.nodeName === node).reduce((a, e) => {
          a[e.nodeId] = {
            nodeId: e.nodeId,
            nodeName: e.nodeName,
            isAvailable: false
          };
          return a;
        }, {})
        this.view[node].replicasKeys = Object.keys(this.view[node].replicas);
        this.view[node].replicaCount = upNodes.filter(n => n === node).length
      }
      console.log(this.view);
    })

    this.availableViewSubscription = this.clusterStatusService.getAvailableView().subscribe(view => {
      console.log("AVAILABLE VIEW", view)
      const availableNodes = view.map(n => n.nodeName);
      for (let node of attendedView) {
        this.view[node].isAvailable = availableNodes.includes(node);
        for (let replica of this.view[node].replicasKeys) {
          this.view[node].replicas[replica].isAvailable = view.map(n => n.nodeId).includes(this.view[node].replicas[replica].nodeId)
        }
      }
    })
  }



  async spawnBundle(node: any) {

    await this.clusterStatusService.spawn(node);
    this.view[node].isOnline = true;
    this.view[node].replicaCount++;
    this.view[node].replicasKeys.push('pending')
    this.view[node].replicas['pending'] = {
      nodeId: 'pending',
      nodeName: 'pending'
    }
  }

  async kill(replica: any) {
    await this.clusterStatusService.kill(replica.nodeId);
    // replica.isAvailable = false;
  }

  ngOnDestroy(): void {
    this.viewSubscription.unsubscribe();
    this.availableViewSubscription.unsubscribe();
  }
}
