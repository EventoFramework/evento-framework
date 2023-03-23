import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {componentColor, graphCenterFit, payloadColor, stringToColour} from '../../services/utils';
import {FlowsService} from '../../services/flows.service';
import {CatalogService} from '../../services/catalog.service';
import {BundleService} from '../../services/bundle.service';

declare let mxGraph: any;
declare let mxHierarchicalLayout: any;
declare let mxEvent: any;


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

  search = '';


  private network: any;

  constructor(private flowService: FlowsService,
              private catalogService: CatalogService,
              private bundleService: BundleService,
              private route: ActivatedRoute) {

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

    this.route.queryParamMap.subscribe(async q => this.setNetwork(await this.loadNetworkFromQuery(q)));
  }

  loadNetworkFromQuery(queryParamMap) {
    const component = queryParamMap.get('component');
    if (component) {

      return this.flowService.getQueueNetFilter('component', component);
    }
    const bundle = queryParamMap.get('bundle');
    if (bundle) {

      return this.flowService.getQueueNetFilter('bundle', bundle);
    }
    const payload = queryParamMap.get('payload');
    if (payload) {

      return this.flowService.getQueueNetFilter('payload', payload);
    }
    const handler = queryParamMap.get('handler');
    if (handler) {

      return this.flowService.getQueueNetFilter('handler', handler);
    }
    return this.flowService.getQueueNet();
  }

  async setNetwork(network) {

    const container = this.container.nativeElement;
    this.network = network;
    this.sources = [];
    const tMap = {};
    for (const node of this.network.nodes) {
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
        node.numServers = node.numServers / tMap[block].length;
        node.fcr = true;
      }
    }


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
        if (e.ctrlKey) {
          e.preventDefault();
          e.stopPropagation();
          if (e.wheelDelta > 0) {
            graph.zoomIn();
          } else {
            graph.zoomOut();
          }
        }
      });


      const edges = [];
      const layout = new mxHierarchicalLayout(graph, 'west');
      layout.traverseAncestors = false;
      graph.getModel().beginUpdate();
      try {

        const nodesRef = {};

        for (const node of this.network.nodes) {
          nodesRef[node.id] = node;
          if (node.type !== 'Source') {
            node.throughtput = 0;
          }
          node.flowThroughtput = 0;
        }


        if (this.performanceAnalysis) {
          this.doPerformanceAnalysis(nodesRef);

        }


        const vertexRef = {};

        const sinkStyle = 'shape=ellipse;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;' +
          'fontColor=black;fillColor=transparent';
        const edgeStyle = 'edgeStyle=elbowEdgeStyle;rounded=1;jumpStyle=arc;orthogonalLoop=1;jettySize=auto;html=1;' +
          'endArrow=block;endFill=1;orthogonal=1;strokeWidth=1;';
        for (const node of this.network.nodes) {

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

          if (this.performanceAnalysis && node.isBottleneck && node.type !== 'Source') {
            vertexRef[node.id].style += 'strokeColor=#ff0000;strokeWidth=3;';
          }

          if (this.performanceAnalysis) {
            vertexRef[node.id].value +=
              `<br/><br/>Service time: ${node.meanServiceTime.toFixed(4)}  [ms]`+
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


        for (const node of this.network.nodes) {
          const targets = [];
          for (const t of Object.keys(node.target)) {
            targets.push(nodesRef[t]);
          }
          for (const target of targets.sort((a, b) => a?.async - b?.async)) {
            if (this.performanceAnalysis) {
              const source = nodesRef[node.id];
              const ql = (source.throughtput - target.throughtput) * target.meanServiceTime;
              const ratio = source.throughtput / source.flowThroughtput;
              const c = this.perc2color(ratio * 100);
              let txt = (node.throughtput * node.target[target.id]).toFixed(4) + '  [r/ms]';
              if (target.fcr) {
                txt += '\n' + ql.toFixed(4) + ' [ql/ms]';
              }
              txt += '\n' + (node.target[target.id] * 100)?.toFixed(1) + ' %';

              const zz = ';strokeWidth=' + String((Math.max(1, Math.min(ratio * 5, 10)))) + ';';
              const sty = edgeStyle + zz + ';strokeColor=' + c + ';' + (target.async ? 'dashed=1' : 'dashed=0');
              graph.insertEdge(parent, null, txt, vertexRef[node.id],
                vertexRef[target.id], (!!ratio && !!node.target[target.id]) ? sty :
                  (edgeStyle + ';strokeWidth=1;strokeColor=grey' + ';' + (target.async ? 'dashed=1' : 'dashed=0')));
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

    let old = -1;
    let i = 0;

    while (true) {

      for (const node of this.network.nodes) {
        nodesRef[node.id] = node;
        if (node.type !== 'Source') {
          node.throughtput = 0;
        }
        node.flowThroughtput = 0;
      }

      const q = [];
      for (const s of this.sources) {
        s.meanServiceTime = 1 / s.throughtput;
        s.flowThroughtput = s.throughtput;
        s.flow = s.id;
        q.push(s);
      }
      while (q.length > 0) {
        const n = q.shift();
        for (const t of Object.keys(n.target)) {
          const target = nodesRef[t];
          target.throughtput = (n.throughtput * n.target[t]);
          if (target.fcr) {
            const tt = target.numServers / target.meanServiceTime;
            if (tt < target.throughtput) {
              target.throughtput = tt;
            }
          }
          if (target.throughtput > n.throughtput || n.target[t] !== 1) {
            target.flowThroughtput = target.throughtput;
            target.flow = target.id;
          } else {
            target.flow = n.flow;
            target.flowThroughtput = n.flowThroughtput;
          }
          q.push(target);
        }
      }

      this.bundleActiveThreads = {};
      this.maxFlowThroughput = {};

      for (const node of this.network.nodes) {
        const nc = node.throughtput * node.meanServiceTime;
        node.customers = (node.fcr ? Math.max(node.numServers, nc) : nc);
        if (node.bundle) {
          if (!this.bundleActiveThreads[node.bundle]) {
            this.bundleActiveThreads[node.bundle] = 0;
          }
          this.bundleActiveThreads[node.bundle] += node.customers;
          if (!this.bundles.includes(node.bundle)) {
            this.bundles.push(node.bundle);
          }
        }
        node.isBottleneck = false;
        if (!this.maxFlowThroughput[node.flow]) {
          this.maxFlowThroughput[node.flow] = node;
          //node.isBottleneck = true;
        } else if (nodesRef[node.flow].throughtput > node.throughtput && node.type !== 'Sink') {
          if (this.maxFlowThroughput[node.flow].throughtput > node.throughtput) {
            this.maxFlowThroughput[node.flow] = node;
          }
          node.isBottleneck = true;
        }
      }

      let tSum = 0;
      const tMap = {};
      for (const node of this.network.nodes) {
        tSum += node.throughtput;
        if (node.fcr) {
          if (!tMap[node.component]) {
            tMap[node.component] = {
              t: node.flowThroughtput,
              s: 0
            };
          }
          tMap[node.component].t = Math.min(tMap[node.component].t, node.flowThroughtput);
          tMap[node.component].s += node.meanServiceTime;
        }
      }
      console.log(tMap);
      console.log(tSum - old);
      console.log(i);
      if (Math.abs(tSum - old) > 0.00001 && i < 10) {
        old = tSum;
        i++;

        const nsSum = {};
        for (const node of this.network.nodes) {
          if (node.fcr) {
            node.numServers = (node.flowThroughtput / tMap[node.component].t) * (node.meanServiceTime / tMap[node.component].s);
            if (!nsSum[node.component]) {
              nsSum[node.component] = 0;
            }
            nsSum[node.component] += node.numServers;
          }
        }
        for (const node of this.network.nodes) {
          if (node.fcr) {
            node.numServers = Math.min(
              node.numServers / nsSum[node.component],
              node.flowThroughtput * node.meanServiceTime);
          }
        }

      } else {
        return;
      }
    }
  }
}
