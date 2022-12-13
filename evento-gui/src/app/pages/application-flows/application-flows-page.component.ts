import {Component, OnInit} from '@angular/core';
import {HandlerService} from '../../services/handler.service';
import {BundleColorService} from '../../services/bundle-color.service';
import * as mermaid from 'mermaid';
import {ActivatedRoute} from "@angular/router";

declare let mxGraph: any;
declare let mxClient: any;
declare let mxRectangle: any;
declare let mxHierarchicalLayout: any;
declare let mxGraphView: any;

@Component({
  selector: 'app-application-petri-net',
  templateUrl: './application-flows-page.component.html',
  styleUrls: ['./application-flows-page.component.scss'],
})
export class ApplicationFlowsPage implements OnInit {
  performanceAnalysis = false;
  sources = [];
  private network: any;

  bundleActiveThreads = {};
  maxFlowThroughput = {};
  bundles = [];

  constructor(private handlerService: HandlerService,
              private bundleColorService: BundleColorService,
              private route: ActivatedRoute) {
  }


  async ngOnInit() {

    const handlerId = this.route.snapshot.params.handlerId;
    const container = document.getElementById('flows');
    this.network = handlerId === 'all' ? await this.handlerService.getQueueNet() : await this.handlerService.getQueueNet(handlerId);

    this.sources = [];


    const tMap = {}
    for (const node of this.network.nodes) {
      if (node.type === 'Source') {
        node.throughtput = 0.01;
        node.meanServiceTime = 0.01;
        this.sources.push(node);
      }
      if (!node.meanServiceTime) {
        node.meanServiceTime = 0.0;
      }
      if(node.numServers){
        if(!tMap[node.bundle + node.component + node.numServers]){
          tMap[node.bundle + node.component + node.numServers] = [];
        }
        tMap[node.bundle + node.component + node.numServers].push(node);
      }
    }

    for(const block in tMap){
      for(const node of tMap[block]){
        node.numServers = node.numServers ; /// tMap[block].length;
      }
    }
    console.log(tMap);


    this.drawGraph(container);
  }


  togglePerformanceAnalysis(event: any) {
    this.performanceAnalysis = event.detail.checked;
    return this.drawGraph(document.getElementById('flows'));
  }

  private drawGraph(container) {
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


    const layout = new mxHierarchicalLayout(graph, 'west');
    layout.traverseAncestors = false;
    graph.getModel().beginUpdate();
    try {

      const nodesRef = {};
      for (const node of this.network.nodes) {
        nodesRef[node.id] = node;
        if(node.type != 'Source') {
          node.throughtput = 0;
        }
        node.flowThroughtput = 0;
      }


      if (this.performanceAnalysis) {
        var q = [];
        for(const s of this.sources){
          s.meanServiceTime = 1 / s.throughtput;
          s.flowThroughtput = s.throughtput;
          s.flow = s.id;
          q.push(s);
        }
        while (q.length > 0){
          const n = q.shift();
          for(const t of n.target){
            var target = nodesRef[t];
            target.throughtput += n.throughtput;
            if(target.numServers){
              const t = target.numServers / target.meanServiceTime;
              if(t < target.throughtput){
                target.throughtput = t;
              }
            }
            if(!target.flowThroughtput || (target.flowThroughtput > n.flowThroughtput))
              target.flowThroughtput = n.flowThroughtput;
            target.flow = n.flow;
            q.push(target);
          }
        }

        this.bundleActiveThreads = {};
        this.maxFlowThroughput = {};

        for (const node of this.network.nodes) {
          const nc = node.throughtput * node.meanServiceTime;
          node.customers = (node.numServers ? Math.max(node.numServers, nc) : nc);
          if(node.bundle) {
            if (!this.bundleActiveThreads[node.bundle]) {
              this.bundleActiveThreads[node.bundle] = 0;
            }
            this.bundleActiveThreads[node.bundle] += node.customers;
            if(!this.bundles.includes(node.bundle)){
              this.bundles.push(node.bundle);
            }
          }
          node.isBottleneck = false;
          if(!this.maxFlowThroughput[node.flow]){
            this.maxFlowThroughput[node.flow] = node;
            node.isBottleneck = true;
          }else if(nodesRef[node.flow].throughtput > node.throughtput && node.type !== 'Sink') {
            //this.maxFlowThroughput[node.flow].isBottleneck = false;
            if(this.maxFlowThroughput[node.flow].throughtput >  node.throughtput) {
              this.maxFlowThroughput[node.flow] = node;
            }
            node.isBottleneck = true;
          }
        }

      }


      const vertexRef = {}

      const serviceStationStyle = 'shape=rectangle;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;verticalAlign=top;horizontal=1;labelPosition=center;verticalLabelPosition=middle;align=center;';
      const sinkStyle = 'shape=ellipse;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;fillColor=transparent';
      const performanceBoxStyle = '';
      const edgeStyle = 'endArrow=classic;html=1;labelBackgroundColor=white;elbow=vertical;edgeStyle=orthogonalEdgeStyle;curved=1;';
      for (const node of this.network.nodes) {
        if (node.type === 'Sink') {
          node.name = node.type;
          vertexRef[node.id] = graph.insertVertex(parent, node.id, "Sink", null, null, 50,
            50,
            sinkStyle);
        } else if (node.type === 'Source') {
          var text = '\n' + node.name;
          vertexRef[node.id] = graph.insertVertex(parent, node.id, text, null, null, text.length * 10,
            40,
            serviceStationStyle);
        } else {
          node.name = node.bundle + '\n\n' + node.component + '\n\n' + node.action;
          let additionalStyles = 'fillColor=' + this.bundleColorService.getColorForBundle(node.bundle) + ';';
          if(node.isBottleneck){
            console.log(node)
            additionalStyles += 'strokeColor=#ff0000;strokeWidth=3;'
          }
          const width = Math.max(node.bundle.length, node.component.length, node.action.length, this.performanceAnalysis ? 25 : 0) * 10;
          let height = 90;
          let text = node.name;
          /*
          if(this.performanceAnalysis){
            text += '\n'
            height += 30;
          }
          if (node.meanServiceTime) {
            text += '\n\nmst: ' + node.meanServiceTime.toFixed(3) + ' [ms]';
            height += 30;
          }*/

          if (node.bundle === 'event-store' || node.component === 'SagaStore' || node.component === 'ProjectorStore') {
            additionalStyles += 'shape=cylinder;verticalAlign=bottom;spacingBottom=' + (this.performanceAnalysis ? 100 : 20) + ';';
            height += 70;
          }
          const bHeight = height;
          if (this.performanceAnalysis && node.meanServiceTime) {
            height += 90;
          }
          vertexRef[node.id] = graph.insertVertex(parent, node.id, '\n' + text, null, null, width,
            height,
            serviceStationStyle + additionalStyles);
          if (this.performanceAnalysis && node.meanServiceTime) {
            let txt = 'Service time: ' + (node.meanServiceTime.toFixed(4)) + ' [ms]';
            const w = 210;
            graph.insertVertex(vertexRef[node.id], node.id + '_st', txt, (width / 2) - (w / 2), bHeight + 10, w,
              30,
              performanceBoxStyle);


            txt = 'Customers: '+node.customers.toFixed(4)+(node.numServers ? ('/' + node.numServers.toFixed(4)) : '')+' [r]';
            graph.insertVertex(vertexRef[node.id], node.id + '_cn', txt, (width / 2) - (w / 2), bHeight + 30 + 15, w,
              30,
              performanceBoxStyle);
          }
        }
      }


      for (const node of this.network.nodes) {
        const targets = [];
        for (const t of node.target) {
          targets.push(nodesRef[t]);
        }
        for (const target of targets.sort((a, b) => a?.async - b?.async)) {
          if (this.performanceAnalysis) {
            const source = nodesRef[node.id];
            const ql = (source.throughtput - target.throughtput) * target.meanServiceTime;
            const ratio = source.throughtput / source.flowThroughtput;
            const c = this.perc2color(ratio * 100);
            var txt = node.throughtput.toFixed(4) + "  [r/ms]";
            txt += "\n" + ql.toFixed(4) + " [ql/ms]";
            graph.insertEdge(parent, null, txt, vertexRef[node.id],
              vertexRef[target.id], edgeStyle + ';' + (target.async ? 'dashed=1' : 'dashed=0') + ';strokeWidth=' + (ratio * 20) + ';strokeColor=' + c);
          } else {
            graph.insertEdge(parent, null, "", vertexRef[node.id],
              vertexRef[target.id], edgeStyle + ';' + (target.async ? 'dashed=1' : 'dashed=0'));
          }
        }
      }


      // Executes the layout
      layout.execute(parent);
    } finally {
      graph.getModel().endUpdate();
    }

  }

  perc2color(perc) {
    let r, g, b = 0;
    if(perc < 50) {
      r = 255;
      g = Math.round(5.1 * perc);
    }
    else {
      g = 255;
      r = Math.round(510 - 5.10 * perc);
    }
    const h = r * 0x10000 + g * 0x100 + b * 0x1;
    return '#' + ('000000' + h.toString(16)).slice(-6);
  }

  runAnalysis() {
    return this.drawGraph(document.getElementById('flows'));
  }
}
