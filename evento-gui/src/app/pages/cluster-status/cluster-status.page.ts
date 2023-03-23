import {Component, OnDestroy, OnInit} from '@angular/core';
import {ClusterStatusService} from '../../services/cluster-status.service';
import {Subscription} from 'rxjs';
import {stringToColour} from '../../services/utils';

@Component({
  selector: 'app-cluster-status',
  templateUrl: './cluster-status.page.html',
  styleUrls: ['./cluster-status.page.scss'],
})
export class ClusterStatusPage implements OnInit, OnDestroy {

  bundleColor = {};
  public view = {};
  public attendedView = [];
  public externalView = [];
  private viewSubscription: Subscription;
  constructor(private clusterStatusService: ClusterStatusService) {
  }

  async ngOnInit() {

    const attendedView = await this.clusterStatusService.getAttendedView();
    for (const node of attendedView) {
      this.bundleColor[node] = stringToColour(node);
      this.view[node] = {
        isOnline: false,
        isAvailable: false,
        replicas: {},
        replicaCount: 0,
        external: false
      };
    }
    this.attendedView = attendedView;

    this.viewSubscription = this.clusterStatusService.getView().subscribe(viewUpdate => {
      const view = viewUpdate.view;
      if (viewUpdate.type === 'current') {
        const upNodes = view.map(n => n.bundleId);
        this.externalView = [...new Set(upNodes.filter(n => !this.attendedView.includes(n)))];
        for (const node of this.externalView) {
          if(!this.view[node]) {
            this.bundleColor[node] = stringToColour(node);
            this.view[node] = {
              isOnline: false,
              isAvailable: false,
              replicas: {},
              replicaCount: 0,
              external: true
            };
          }
        }
        for (const node of this.attendedView.concat(this.externalView)) {
          this.bundleColor[node] = stringToColour(node);
          this.view[node].isOnline = upNodes.includes(node);
          this.view[node].replicas = view.filter(n => n.bundleId === node).reduce((a, e) => {
            a[e.nodeId] = {
              nodeId: e.nodeId,
              bundleId: e.bundleId,
              bundleVersion: e.bundleVersion,
              isAvailable: this.view[node]?.replicas[e.nodeId]?.isAvailable
            };
            return a;
          }, {});
          this.view[node].replicasKeys = Object.keys(this.view[node].replicas);
          this.view[node].replicaCount = upNodes.filter(n => n === node).length;
        }
      } else {
        const availableNodes = view.map(n => n.bundleId);
        for (const node of this.attendedView.concat(this.externalView)) {
          this.bundleColor[node] = stringToColour(node);
          this.view[node].isAvailable = availableNodes.includes(node);
          for (const replica of this.view[node].replicasKeys) {
            this.view[node].replicas[replica].isAvailable = view.map(n => n.nodeId).includes(this.view[node].replicas[replica].nodeId);
          }
        }
      }
    });
  }


  async spawnBundle(node: any) {

    await this.clusterStatusService.spawn(node);
    this.view[node].isOnline = true;
    this.view[node].replicaCount++;
    this.view[node].replicasKeys.push('pending');
    this.view[node].replicas.pending = {
      nodeId: 'pending',
      bundleId: 'pending'
    };
  }

  async kill(replica: any) {
    await this.clusterStatusService.kill(replica.nodeId);
    // replica.isAvailable = false;
  }

  ngOnDestroy(): void {
    this.viewSubscription.unsubscribe();
  }
}
