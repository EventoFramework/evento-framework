import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {componentColor, graphCenterFit, payloadColor, stringToColour} from '../../services/utils';
import {FlowsService} from '../../services/flows.service';
import {CatalogService} from '../../services/catalog.service';
import {BundleService} from '../../services/bundle.service';
import {AlertController} from "@ionic/angular";

declare let mxGraph: any;
declare let mxHierarchicalLayout: any;
declare let mxEvent: any;
declare let mxXmlCanvas2D: any;
declare let mxUtils: any;
declare let mxImageExport: any;
declare let mxXmlRequest: any;


@Component({
  selector: 'app-application-petri-net',
  templateUrl: './application-flows-page.component.html',
  styleUrls: ['./application-flows-page.component.scss'],
})
export class ApplicationFlowsPage implements OnInit {


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

  constructor(private flowService: FlowsService,
              private catalogService: CatalogService,
              private bundleService: BundleService,
              private route: ActivatedRoute,
              private alertController: AlertController) {

  }

  async ngOnInit() {

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

  loadModelFromQuery(queryParamMap) {
    const component = queryParamMap.get('component');
    if (component) {

      return this.flowService.getPerformanceModelFilter('component', component);
    }
    const bundle = queryParamMap.get('bundle');
    if (bundle) {

      return this.flowService.getPerformanceModelFilter('bundle', bundle);
    }
    const payload = queryParamMap.get('payload');
    if (payload) {

      return this.flowService.getPerformanceModelFilter('payload', payload);
    }
    const handler = queryParamMap.get('handler');
    if (handler) {

      return this.flowService.getPerformanceModelFilter('handler', handler);
    }
    return this.flowService.getPerformanceModel();
  }

  async setModel(model) {

    const container = this.container.nativeElement;

    // Disables built-in context menu
    mxEvent.disableContextMenu(container);

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

    // eslint-disable-next-line guard-for-in
    for (const block in tMap) {
      for (const node of tMap[block]) {
        //node.numServers = node.numServers / tMap[block].length;
        node.fcr = true;
      }
    }


    this.drawGraph(container);
  }

  redrawGraph() {
    const container = this.container.nativeElement;
    this.drawGraph(container);
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

  private drawGraph(container) {
    setTimeout(() => {
      container.innerHTML = '';
      const graph = new mxGraph(container);
      const parent = graph.getDefaultParent();
      graph.setTooltips(true);

      // Enables panning with left mouse button
      graph.panningHandler.useLeftButtonForPanning = true;
      graph.panningHandler.ignoreCell = true;
      graph.container.style.cursor = 'move';
      graph.setPanning(true);
      graph.resizeContainer = false;
      graph.htmlLabels = true;


      container.addEventListener('wheel', (e: any) => {
        e.preventDefault();
        e.stopPropagation();
        if (e.wheelDelta > 0) {
          graph.zoomIn();
        } else {
          graph.zoomOut();
        }
      });


      const edges = [];
      const layout = new mxHierarchicalLayout(graph, this.orientation ? 'west' : 'north');
      layout.traverseAncestors = false;
      graph.getModel().beginUpdate();
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


        const vertexRef = {};

        const sinkStyle = 'shape=ellipse;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;' +
          'fontColor=black;fillColor=transparent';
        const edgeStyle = 'edgeStyle=elbowEdgeStyle;rounded=1;jumpStyle=arc;orthogonalLoop=1;jettySize=auto;html=1;' +
          'endArrow=block;endFill=1;orthogonal=1;strokeWidth=1;';
        for (const node of this.model.nodes) {

          let height = 60;
          if (node.component === 'Gateway') {
            vertexRef[node.id] = graph.insertVertex(parent, node.id,
              `<span class="title" style="color: ${payloadColor[node.actionType]} !important;">${node.action}</span>`,
              null, null, node.action.length * 10 + 25,
              height,
              'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' +
              (node.actionType ? payloadColor[node.actionType] : 'black') + ';fontColor=#333333;strokeWidth=3;');
          } else if (node.type === 'Source') {
            const text = node.name;
            vertexRef[node.id] = graph.insertVertex(parent, node.id,
              `<span class="title" style="color: ${payloadColor[node.actionType]} !important;">${node.action}</span>`,
              null, null, text.length * 10,
              height,
              'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' +
              (node.actionType ? payloadColor[node.actionType] : 'black') + ';fontColor=#333333;strokeWidth=3;');
          } else if (node.bundle === 'event-store') {
            height = 80;
            vertexRef[node.id] = graph.insertVertex(parent, node.id, '\n<span class="title">' + node.action + '</span>',
              null, null, node.action.length * 10 + 30,
              height,
              'shape=cylinder;whiteSpace=wrap;html=1;boundedLbl=1;backgroundOutline=1;size=15;rounded=1;whiteSpace=wrap;' +
              'html=1;fillColor=#ffffff;strokeColor=#000000;fontColor=#333333;strokeWidth=3;');
          } else if (node.type === 'Sink') {
            node.name = node.type;
            height = 50;
            vertexRef[node.id] = graph.insertVertex(parent, node.id, 'Sink', null, null, 50,
              height,
              sinkStyle);
          } else {
            height = 80;
            vertexRef[node.id] = graph.insertVertex(parent, node.id,
              `<b style="color: ${stringToColour(node.bundle)}">${node.bundle}</b>
                <span class="title" style="color: ${componentColor[node.componentType]} !important">${node.component}</span>`
              , null, null, Math.max(node.component.length, node.bundle.length) * 10 + 25,
              height,
              'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + stringToColour(node.bundle) +
              ';fontColor=#333333;strokeWidth=3;');
          }

          vertexRef[node.id].nodeId = node.id;
          vertexRef[node.id].handlerId = node.handlerId;

          if (this.performanceAnalysis && node.isBottleneck && node.type !== 'Source') {
            vertexRef[node.id].style += 'strokeColor=#ff0000;strokeWidth=3;';
          }

          if (this.performanceAnalysis && node.meanServiceTime) {
            vertexRef[node.id].value +=
              `<br/><br/>Service time: ${node.meanServiceTime.toFixed(4)}  [ms]` +
              `<br/>Customers: ${node.customers.toFixed(4) + (node.fcr ? ('/' + 1) : '')} [r]`;
            vertexRef[node.id].geometry.height += 30;
            if (node.bundle === 'event-store') {
              vertexRef[node.id].geometry.height += 30;
              vertexRef[node.id].value = '<br/><br/>' + vertexRef[node.id].value;
            }
            //vertexRef[node.id].value += "<br/>" +


          }
          if (this.performanceAnalysis) {
            // vertexRef[node.id].value += "<br/>" +  JSON.stringify(nodesRef[node.flow].throughtput) + "-" + node.throughtput
          }
        }


        for (const node of this.model.nodes) {
          const targets = [];
          for (const t of Object.keys(node.target)) {
            targets.push(nodesRef[t]);
          }
          for (const target of targets.sort((a, b) => a?.async - b?.async)) {
            if (this.performanceAnalysis) {
              const source = nodesRef[node.id];
              const ql = (source.throughtput - target.throughtput);
              const ratio = source.throughtput === 0 ? 0 : (source.throughtput*1.00) / (source.workload*1.00);
              const c = this.perc2color(ratio * 100.0);
              console.log(ratio, c);
              console.log(source.throughtput, source.workload);
              console.log(source);
              let txt = (node.throughtput * node.target[target.id]).toFixed(4) + '  [r/ms]';
              if (target.fcr) {
                txt += '\n' + ql.toFixed(4) + ' [ql/ms]';
              }
              txt += '\n' + (node.target[target.id] * 100)?.toFixed(1) + ' %';
              const zz = ';strokeWidth=' + String((Math.max(1, Math.min(ratio * 5, 10)))) + ';';
              const sty = edgeStyle + zz + ';strokeColor=' + c + ';' + (target.async ? 'dashed=1' : 'dashed=0');
              const defS = (!!ratio && !!node.target[target.id]) ? sty :
                (edgeStyle + ';strokeWidth=1;strokeColor=grey' + ';' + (target.async ? 'dashed=1' : 'dashed=0'));
              console.log(defS);
              graph.insertEdge(parent, null, txt, vertexRef[node.id],
                vertexRef[target.id],defS );
            } else {
              edges.push(graph.insertEdge(parent, null, '', vertexRef[node.id],
                vertexRef[target.id], edgeStyle + ';' + (target.async ? 'dashed=1' : 'dashed=0') + ';' +
                (target.async ? 'strokeColor=#999999' : 'strokeColor=#000')));
            }
          }
        }


        // Executes the layout
        layout.execute(parent);
      } finally {
        graph.getModel().endUpdate();
      }

      // Configures automatic expand on mouseover
      graph.popupMenuHandler.autoExpand = true;
      // Installs a popupmenu handler using local function (see below).
      graph.popupMenuHandler.factoryMethod = (menu, cell, evt) => {
        if (cell?.vertex) {
          console.log(cell);
          if (this.performanceAnalysis) {
            const node = this.model.nodes.find(n => (n.id === cell.nodeId));
            if (node) {
              const copies = this.model.nodes.filter(n => n.handlerId === node.handlerId);
              menu.addItem('Edit Mean Service Time (' + (node.meanServiceTime || 1).toFixed(4) + ' [ms])', '', async () => {
                const alert = await this.alertController.create({
                  header: 'Edit Mean Service Time',
                  subHeader: node.component + ' - ' + node.action,
                  inputs: [
                    {
                      id: 'mst',
                      name: 'mst',
                      value: node.meanServiceTime
                    }
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
                  ]
                });
                await alert.present();
              });
              menu.addSeparator();

              for (const t of Object.keys(node.target)) {
                const i = this.model.nodes.find(n => parseInt(n.id, 10) === parseInt(t, 10));
                menu.addItem(i.action + ' (' + node.target[t] + ')', '', async () => {
                  const alert = await this.alertController.create({
                    header: 'Edit Invocation Frequency',
                    subHeader: node.component + ' - ' + i.action,
                    inputs: [
                      {
                        id: 'inf',
                        name: 'inf',
                        value: node.target[t]
                      }
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
                            // eslint-disable-next-line guard-for-in
                            for (const k in tt.target) {
                              if (this.model.nodes.find(n => parseInt(n.id, 10) === parseInt(k, 10)).action === i.action) {
                                tt.target[k] = parseFloat(e.inf);
                              }
                            }
                          }
                          this.redrawGraph();
                          //this.redrawGraph();
                        },
                      },
                    ]
                  });
                  await alert.present();
                });
              }
            }
          } else {
            const t = this.model.nodes.find(n => n.id === cell.nodeId);
            if (t) {
              console.log(t);
              if (t.path) {
                for (const line of t.lines) {
                  menu.addItem('Open Repository (' + line + ')','',
                    () => {
                      window.open(t.path + '#' + t.linePrefix + line, '_blank');
                    });
                }
              }
            }
          }
        }
      };


      graphCenterFit(graph, container);

      for (const e of edges) {
        const state = graph.view.getState(e);
        state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
      }

      graph.view.addListener(mxEvent.AFTER_RENDER, () => {
        for (const e of edges) {
          const state = graph.view.getState(e);
          state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
        }
      });
    }, 500);

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
            console.log(this.maxFlowThroughput[node.flow].id === source.id);
            if (this.maxFlowThroughput[node.flow].id === source.id ||
              this.maxFlowThroughput[node.flow].numServers < node.numServers
            ) {
              this.maxFlowThroughput[node.flow] = node;
            }
          }
          /*
          node.isBottleneck = false;
          if (nodesRef[node.flow].throughtput > node.throughtput && node.type !== 'Sink') {
            console.log(node);
            let c = node.flow;
            while (c){
              console.log(c);
              console.log(nodesRef[c])
              if(nodesRef[c]?.flow == c){
                break;
              }
              c = nodesRef[c]?.flow
            }
            /*
            if (this.maxFlowThroughput[node.flow].throughtput > node.throughtput) {
              this.maxFlowThroughput[node.flow] = node;
            }
            node.isBottleneck = true;
          }
        */
        }
        return;
      }
    }
  }

  setTp(source: any, $event: any) {
    source.throughtput = 1 / $event.target.value
  }
}
