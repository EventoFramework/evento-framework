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
  constructor(private clusterStatusService: ClusterStatusService) {
  }

  async ngOnInit() {

    const attendedView = await this.clusterStatusService.getAttendedView();
    for (let node of attendedView) {
      this.view[node] = {
        isOnline: false,
        isAvailable: false,
        replicas: {},
        replicaCount: 0,
        external: false
      }
    }
    this.attendedView = attendedView;

    this.viewSubscription = this.clusterStatusService.getView().subscribe(viewUpdate => {
      const view = viewUpdate.view;
      if (viewUpdate.type === 'current') {
        const upNodes = view.map(n => n.bundleId);
        this.externalView = [...new Set(upNodes.filter(n => !this.attendedView.includes(n)))]
        for (let node of this.externalView) {
          if(!this.view[node]) {
            this.view[node] = {
              isOnline: false,
              isAvailable: false,
              replicas: {},
              replicaCount: 0,
              external: true
            }
          }
        }
        for (let node of this.attendedView.concat(this.externalView)) {
          this.view[node].isOnline = upNodes.includes(node);
          this.view[node].replicas = view.filter(n => n.bundleId === node).reduce((a, e) => {
            a[e.nodeId] = {
              nodeId: e.nodeId,
              bundleId: e.bundleId,
              isAvailable: this.view[node]?.replicas[e.nodeId]?.isAvailable
            };
            return a;
          }, {})
          this.view[node].replicasKeys = Object.keys(this.view[node].replicas);
          this.view[node].replicaCount = upNodes.filter(n => n === node).length
        }
      } else {
        const availableNodes = view.map(n => n.bundleId);
        for (let node of this.attendedView.concat(this.externalView)) {
          this.view[node].isAvailable = availableNodes.includes(node);
          for (let replica of this.view[node].replicasKeys) {
            this.view[node].replicas[replica].isAvailable = view.map(n => n.nodeId).includes(this.view[node].replicas[replica].nodeId)
          }
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
      bundleId: 'pending'
    }
  }

  async kill(replica: any) {
    await this.clusterStatusService.kill(replica.nodeId);
    // replica.isAvailable = false;
  }

  ngOnDestroy(): void {
    this.viewSubscription.unsubscribe();
  }
}
