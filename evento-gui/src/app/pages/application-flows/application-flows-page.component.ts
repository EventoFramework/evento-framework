import {ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {payloadColor, stringToColour} from '../../services/utils';
import {FlowsService} from '../../services/flows.service';
import {CatalogService} from '../../services/catalog.service';
import {BundleService} from '../../services/bundle.service';
import {RepositoryService} from '../../services/repository.service';
import {AlertController, LoadingController} from '@ionic/angular';
import {
  createEventoGraph,
  cssToken,
  EventoEdge,
  EventoGraphHandle,
  EventoNode,
} from '../../components/graph/evento-graph';

@Component({
  selector: 'app-application-petri-net',
  templateUrl: './application-flows-page.component.html',
  styleUrls: ['./application-flows-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: false,
})
export class ApplicationFlowsPage implements OnInit, OnDestroy {


  @ViewChild('container', {static: true}) container: ElementRef;

  performanceAnalysis = false;
  sources = [];

  bundleActiveThreads = {};
  maxFlowThroughput = {};
  bundles = [];
  payloads;
  components;
  bundleFilter;
  allPayloads;
  allComponents;
  allBundles;

  orientation = true;

  search = '';


  private model: any;
  private graph: EventoGraphHandle | null = null;

  constructor(private flowService: FlowsService,
              private catalogService: CatalogService,
              private bundleService: BundleService,
              private repository: RepositoryService,
              private route: ActivatedRoute,
              private alertController: AlertController,
              private loadingController: LoadingController) {

  }

  async ngOnInit() {

    await this.repository.whenReady();
    this.allPayloads = await this.catalogService.findAllPayload();
    this.allPayloads = this.allPayloads.filter(p => p.type !== 'View');
    this.allComponents = await this.catalogService.findAllComponent();
    this.allBundles = await this.bundleService.findAll();
    for (const b of this.allBundles) {
      b.color = stringToColour(b.id);
    }
    this.checkFilter();

    this.route.queryParamMap.subscribe(async q => this.setModel(await this.loadModelFromQuery(q)));
  }

  ngOnDestroy() {
    this.graph?.destroy();
    this.graph = null;
  }

  async loadModelFromQuery(queryParamMap) {
    const loading = await this.loadingController.create({
      message: 'Fetching flow from server...',
    });
    await loading.present();
    try {
      const component = queryParamMap.get('component');
      if (component) {

        return await this.flowService.getPerformanceModelFilter('component', component);
      }
      const bundle = queryParamMap.get('bundle');
      if (bundle) {

        return await this.flowService.getPerformanceModelFilter('bundle', bundle);
      }
      const payload = queryParamMap.get('payload');
      if (payload) {

        return await this.flowService.getPerformanceModelFilter('payload', payload);
      }
      const handler = queryParamMap.get('handler');
      if (handler) {

        return await this.flowService.getPerformanceModelFilter('handler', handler);
      }
      return await this.flowService.getPerformanceModel();
    } finally {
      await loading.dismiss();
    }
  }

  setModel(model) {
    this.model = model;
    this.sources = [];
    const tMap = {};
    for (const node of this.model.nodes) {
      if (node.type === 'Source') {
        node.throughtput = 0.001;
        node.meanServiceTime = 1000;
        this.sources.push(node);
      }
      if (!node.meanServiceTime) {
        node.meanServiceTime = 0;
      }
      if (node.numServers) {
        if (!tMap[node.bundle + node.component + node.numServers]) {
          tMap[node.bundle + node.component + node.numServers] = [];
        }
        tMap[node.bundle + node.component + node.numServers].push(node);
      }
    }

    for (const block in tMap) {
      for (const node of tMap[block]) {
        node.fcr = true;
      }
    }


    return this.drawGraph(this.container.nativeElement);
  }

  redrawGraph() {
    return this.drawGraph(this.container.nativeElement);
  }

  togglePerformanceAnalysis(event: any) {
    this.performanceAnalysis = event.detail.checked;
    return this.drawGraph(this.container.nativeElement);
  }

  runAnalysis() {
    return this.drawGraph(this.container.nativeElement);
  }

  checkFilter() {
    this.payloads = this.allPayloads.filter(p => p.name.toLowerCase().includes(this.search.toLowerCase()) ||
      p.description?.toLowerCase().includes(this.search.toLowerCase()));
    this.components = this.allComponents.filter(c => c.componentName.toLowerCase().includes(this.search.toLowerCase()) ||
      c.description?.toLowerCase().includes(this.search.toLowerCase()));
    this.bundleFilter = this.allBundles.filter(b => b.id.toLowerCase().includes(this.search.toLowerCase()) ||
      b.description?.toLowerCase().includes(this.search.toLowerCase()));
  }

  private async drawGraph(container) {
    const loading = await this.loadingController.create({
      message: 'Rendering flow...',
    });
    await loading.present();
    try {
      const nodesRef = {};
      for (const node of this.model.nodes) {
        nodesRef[node.id] = node;
        if (node.type !== 'Source') {
          node.throughtput = 0;
        }
        node.workload = node.throughtput;
      }
      if (this.performanceAnalysis) {
        this.doPerformanceAnalysis(nodesRef);
      }

      const textColor = cssToken('--evento-text', '#14201f');
      const gNodes: EventoNode[] = [];
      const gEdges: EventoEdge[] = [];

      for (const node of this.model.nodes) {
        const id = String(node.id);
        let n: EventoNode;
        if (node.component === 'Gateway' || node.type === 'Source') {
          n = {
            id,
            label: node.action,
            color: node.actionType ? payloadColor[node.actionType] : textColor,
            primary: true,
          };
        } else if (node.bundle === 'event-store') {
          n = {
            id,
            label: node.action + this.perfSuffix(node),
            color: textColor,
            shape: 'cylinder',
          };
        } else if (node.type === 'Sink') {
          n = {id, label: 'Sink', color: 'grey', shape: 'ellipse'};
        } else {
          n = {
            id,
            label: `${node.bundle}\n${node.component}` + this.perfSuffix(node),
            color: stringToColour(node.bundle),
          };
        }
        if (this.performanceAnalysis && node.isBottleneck && node.type !== 'Source') {
          n.color = '#ff0000';
          n.primary = true;
        }
        gNodes.push(n);
      }

      for (const node of this.model.nodes) {
        const targets = Object.keys(node.target).map(t => nodesRef[t]).filter(Boolean);
        for (const target of targets.sort((a, b) => (a?.async ? 1 : 0) - (b?.async ? 1 : 0))) {
          if (this.performanceAnalysis) {
            const source = nodesRef[node.id];
            const ql = source.throughtput - target.throughtput;
            const ratio = source.throughtput === 0 ? 0 : source.throughtput / source.workload;
            const active = !!ratio && !!node.target[target.id];
            let txt = (node.throughtput * node.target[target.id]).toFixed(4) + ' [r/ms]';
            if (target.fcr) {
              txt += '\n' + ql.toFixed(4) + ' [ql/ms]';
            }
            txt += '\n' + (node.target[target.id] * 100)?.toFixed(1) + ' %';
            gEdges.push({
              source: String(node.id),
              target: String(target.id),
              flow: false,
              dashed: !!target.async,
              label: txt,
              width: active ? Math.max(1, Math.min(ratio * 5, 10)) : 1,
              color: active ? this.perc2color(ratio * 100.0) : 'grey',
            });
          } else {
            gEdges.push({
              source: String(node.id),
              target: String(target.id),
              flow: true,
              color: target.async ? undefined : textColor,
            });
          }
        }
      }

      this.graph?.destroy();
      container.innerHTML = '';
      this.graph = createEventoGraph(container, gNodes, gEdges, {
        direction: this.orientation ? 'RIGHT' : 'DOWN',
        contextMenu: (nodeId) => this.buildMenuItems(nodeId),
      });
    } finally {
      await loading.dismiss();
    }
  }

  private perfSuffix(node): string {
    if (this.performanceAnalysis && node.meanServiceTime) {
      return `\nService time: ${node.meanServiceTime.toFixed(4)} [ms]` +
        `\nCustomers: ${node.customers.toFixed(4)}${node.fcr ? '/1' : ''} [r]`;
    }
    return '';
  }

  private buildMenuItems(nodeId: string): Array<{label: string; action: () => void}> {
    const node = this.model.nodes.find(n => String(n.id) === nodeId);
    if (!node) {
      return [];
    }
    if (this.performanceAnalysis) {
      const copies = this.model.nodes.filter(n => n.handlerId === node.handlerId);
      const items: Array<{label: string; action: () => void}> = [{
        label: 'Edit Mean Service Time (' + (node.meanServiceTime || 1).toFixed(4) + ' [ms])',
        action: async () => {
          const alert = await this.alertController.create({
            header: 'Edit Mean Service Time',
            subHeader: node.component + ' - ' + node.action,
            inputs: [
              {
                id: 'mst',
                name: 'mst',
                value: node.meanServiceTime,
              },
            ],
            buttons: [
              {
                text: 'Cancel',
                role: 'cancel',
              },
              {
                text: 'OK',
                role: 'confirm',
                handler: (e) => {
                  for (const t of copies) {
                    t.meanServiceTime = parseFloat(e.mst);
                  }
                  this.redrawGraph();
                },
              },
            ],
          });
          await alert.present();
        },
      }];
      for (const t of Object.keys(node.target)) {
        const i = this.model.nodes.find(n => parseInt(n.id, 10) === parseInt(t, 10));
        items.push({
          label: i.action + ' (' + node.target[t] + ')',
          action: async () => {
            const alert = await this.alertController.create({
              header: 'Edit Invocation Frequency',
              subHeader: node.component + ' - ' + i.action,
              inputs: [
                {
                  id: 'inf',
                  name: 'inf',
                  value: node.target[t],
                },
              ],
              buttons: [
                {
                  text: 'Cancel',
                  role: 'cancel',
                },
                {
                  text: 'OK',
                  role: 'confirm',
                  handler: (e) => {
                    for (const tt of copies) {
                      for (const k in tt.target) {
                        if (this.model.nodes.find(n => parseInt(n.id, 10) === parseInt(k, 10)).action === i.action) {
                          tt.target[k] = parseFloat(e.inf);
                        }
                      }
                    }
                    this.redrawGraph();
                  },
                },
              ],
            });
            await alert.present();
          },
        });
      }
      return items;
    }
    const items: Array<{label: string; action: () => void}> = [];
    if (node.path && node.lines) {
      for (const line of node.lines) {
        const link = this.repository.link(node.bundle, node.path, line);
        if (link) {
          items.push({label: 'Open Repository (' + line + ')', action: () => window.open(link, '_blank')});
        }
      }
    }
    return items;
  }


  private perc2color(perc) {
    let r;
    let g;
    const b = 0;
    if (perc < 50) {
      r = 255;
      g = Math.round(5.1 * perc);
    } else {
      g = 255;
      r = Math.round(510 - 5.10 * perc);
    }
    const h = r * 0x10000 + g * 0x100 + b;
    return '#' + ('000000' + h.toString(16)).slice(-6);
  }


  private doPerformanceAnalysis(nodesRef) {

    let i = 0;

    for (const node of this.model.nodes) {
      nodesRef[node.id] = node;
      if (node.type !== 'Source') {
        node.throughtput = 0;
      }
      node.workload = node.throughtput;
      node.mf = 1;
      node.isBottleneck = false;
      node.mainBottleneck = null;
    }

    while (true) {

      this.bundleActiveThreads = {};
      this.maxFlowThroughput = {};

      const q = [];
      for (const s of this.sources) {
        s.meanServiceTime = 1 / s.throughtput;
        this.maxFlowThroughput[s.id] = s;
        s.flow = s.id;
        q.push(s);
      }
      while (q.length > 0) {
        const n = q.shift();
        for (const t of Object.keys(n.target)) {
          const target = nodesRef[t];
          target.workload = (n.throughtput * n.target[t]);
          target.throughtput = target.workload;
          if (target.fcr) {
            const tt = target.numServers / target.meanServiceTime;
            if (tt < target.workload) {
              target.throughtput = tt;
              target.isBottleneck = true;
            } else {
              target.isBottleneck = false;
            }
          }
          target.flow = n.flow;
          target.mf = n.mf * n.target[t];
          target.mainBottleneck = n.mainBottleneck;
          if (n.isBottleneck && !target.mainBottleneck) {
            target.mainBottleneck = n.id;
          }
          q.push(target);
        }
      }


      i++;
      const nsSum = {};
      for (const node of this.model.nodes) {
        if (node.fcr) {
          if (!nsSum[node.component]) {
            nsSum[node.component] = 0;
          }
          nsSum[node.component] += node.workload * node.meanServiceTime;
        }
      }
      let diff = 0;
      for (const node of this.model.nodes) {
        if (node.fcr) {
          const o = node.numServers;
          node.numServers = (node.workload * node.meanServiceTime) / Math.max(1, nsSum[node.component]);
          diff += Math.abs(o - node.numServers);
        }
      }
      if (i > 10 || diff < 0.0001) {
        for (const node of this.model.nodes) {
          const nc = node.workload * node.meanServiceTime;
          node.customers = (node.fcr ? Math.min(node.numServers, nc) : nc);
          if (node.bundle) {
            if (!this.bundleActiveThreads[node.bundle]) {
              this.bundleActiveThreads[node.bundle] = 0;
            }
            this.bundleActiveThreads[node.bundle] += node.customers;
            if (!this.bundles.includes(node.bundle)) {
              this.bundles.push(node.bundle);
            }
          }
          if (node.isBottleneck && !node.mainBottleneck) {
            const source = nodesRef[node.flow];
            if (this.maxFlowThroughput[node.flow].id === source.id ||
              this.maxFlowThroughput[node.flow].numServers < node.numServers
            ) {
              this.maxFlowThroughput[node.flow] = node;
            }
          }
        }
        return;
      }
    }
  }

  setTp(source: any, $event: any) {
    source.throughtput = 1 / $event.target.value;
  }
}
