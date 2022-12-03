import {Component, OnInit} from '@angular/core';
import {HandlerService} from '../../services/handler.service';
import {BundleColorService} from '../../services/bundle-color.service';
import * as mermaid from 'mermaid';
import {ActivatedRoute} from "@angular/router";

declare let mxGraph: any;
declare let mxHierarchicalLayout: any;

@Component({
  selector: 'app-application-petri-net',
  templateUrl: './application-flows-page.component.html',
  styleUrls: ['./application-flows-page.component.scss'],
})
export class ApplicationFlowsPage implements OnInit {

  constructor(private handlerService: HandlerService,
              private bundleColorService: BundleColorService,
              private route: ActivatedRoute) {
  }


  async ngOnInit() {

    const handlerId = this.route.snapshot.params.handlerId;
    const container = document.getElementById('flows');
    container.innerHTML = '';

    const graph = new mxGraph(container);
    const parent = graph.getDefaultParent();
    graph.setTooltips(true);

    // Enables panning with left mouse button
    graph.panningHandler.useLeftButtonForPanning = true;
    graph.panningHandler.ignoreCell = true;
    graph.container.style.cursor = 'move';
    graph.setPanning(true);
    graph.resizeContainer = true;

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


    const network = handlerId === 'all' ? await this.handlerService.getQueueNet() : await this.handlerService.getQueueNet(handlerId);


    console.log(network);

    const layout = new mxHierarchicalLayout(graph, 'west');
    graph.getModel().beginUpdate();
    try {

      const nodesRef = {};
      const vertexRef = {}

      const serviceStationStyle = 'shape=rectangle;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;';
      const sinkStyle = 'shape=ellipse;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;fillColor=transparent';
      for (const node of network.nodes) {
        if (node.type === 'Sink') {
          node.name = node.type;
          vertexRef[node.id] = graph.insertVertex(parent, node.id, "Sink", null, null, 50,
            50,
            sinkStyle);
          nodesRef[node.id] = node;
        }  else if (node.type === 'Source') {
          var text = node.name;
          vertexRef[node.id] = graph.insertVertex(parent, node.id, text, null, null, text.length * 10,
            50,
            serviceStationStyle);
          nodesRef[node.id] = node;
        } else {
          node.name = node.bundle + '\n\n' + node.component + '\n\n' + node.action;
          let additionalStyles = 'fillColor=' + this.bundleColorService.getColorForBundle(node.bundle) + ';';
          const width = Math.max(node.bundle.length, node.component.length, node.action.length) * 10;
          let height = 90;
          let text = node.name;
          if (node.meanServiceTime) {
            text += '\n\nmst: ' + node.meanServiceTime.toFixed(3) + ' [ms]';
            height += 30;
          }
          if (node.bundle === 'event-store' || node.component === 'SagaStore' || node.component === 'ProjectorStore') {
            additionalStyles += 'shape=cylinder;verticalAlign=bottom;spacingBottom=20;';
            height += 70;
          }
          vertexRef[node.id] = graph.insertVertex(parent, node.id, text, null, null, width,
            height,
            serviceStationStyle + additionalStyles);
          nodesRef[node.id] = node;
        }
      }

      const edgeStyle = 'endArrow=classic;html=1;labelBackgroundColor=white;elbow=vertical;edgeStyle=orthogonalEdgeStyle;curved=1;';

      for (const node of network.nodes) {
        const targets = [];
        for (const t of node.target) {
          targets.push(nodesRef[t]);
        }
        for (const target of targets.sort((a, b) => a?.async - b?.async)) {
          graph.insertEdge(parent, null, "", vertexRef[node.id],
            vertexRef[target.id], edgeStyle + ';' + (target.async ? 'dashed=1' : 'dashed=0'));
        }
      }


      // Executes the layout
      layout.execute(parent);
    } finally {
      graph.getModel().endUpdate();
    }

  }



}
