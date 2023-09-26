import {Component, ElementRef, Input, OnInit, ViewChild} from '@angular/core';
import {NavController} from '@ionic/angular';
import {componentColor, graphCenterFit, payloadColor} from '../../services/utils';

declare const mxGraph: any;
declare const mxEvent: any;
declare const mxHierarchicalLayout: any;

@Component({
  selector: 'app-bundle-components-diagram',
  templateUrl: './bundle-components-diagram.component.html',
  styleUrls: ['./bundle-components-diagram.component.scss'],
})
export class BundleComponentsDiagramComponent implements OnInit {

  @Input()
  bundle;
  @ViewChild('container', {static: true}) container: ElementRef;

  constructor(private navController: NavController) {
  }

  ngOnInit() {
    setTimeout(() => {

      const container = this.container.nativeElement;

      const graph = new mxGraph(container);
      const parent = graph.getDefaultParent();
      graph.centerZoom = false;
      graph.setTooltips(false);
      graph.setEnabled(false);

      // Enables panning with left mouse button
      graph.panningHandler.useLeftButtonForPanning = true;
      graph.panningHandler.ignoreCell = true;
      graph.container.style.cursor = 'move';
      graph.setPanning(true);
      graph.resizeContainer = false;

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
      const edgeStyle = 'edgeStyle=elbowEdgeStyle;rounded=1;jumpStyle=arc;orthogonalLoop=1;jettySize=auto;html=1;dashed=1;' +
        'endArrow=block;endFill=1;orthogonal=1;strokeColor=#999999;strokeWidth=1;';

      graph.view.addListener(mxEvent.AFTER_RENDER, () => {
        for (const e of edges) {
          const state = graph.view.getState(e);
          state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
        }
      });

      const components = {};
      graph.getModel().beginUpdate();
      try {

        for (const h of this.bundle.handlers) {
          if (h.handlerType === 'EventSourcingHandler') {
            continue;
          }
          if (!components[h.componentName]) {
            components[h.componentName] = graph.insertVertex(parent, '/component-info/' + h.componentName,
              h.componentName, 0, 0, h.componentName.length*7, 50,
              'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + componentColor[h.componentType] +
              ';fontColor=#333333;strokeWidth=3;');

          }
          const p = components[h.componentName];
          const t = graph.insertVertex(parent, '/payload-info/' + h.handledPayload.name, h.handledPayload.name,
            0, 0, h.handledPayload.name.length*7, 50,
            'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + payloadColor[h.handledPayload.type] +
            ';fontColor=#333333;strokeWidth=3;');
          edges.push(graph.insertEdge(parent, null, null, t, p, edgeStyle));

          if (h.returnType) {
            const r = graph.insertVertex(parent, '/payload-info/' + h.returnType.name, h.returnType.name  +
              (h.returnIsMultiple ? '[]' : ''), 0, 0, h.returnType.name.length*7, 50,
              'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + payloadColor[h.returnType.type] +
              ';fontColor=#333333;strokeWidth=3;');
            edges.push(graph.insertEdge(parent, null, null, p, r, edgeStyle));
          }


          for (const i of Object.values(h.invocations) as any[]) {
            const ii = graph.insertVertex(parent, '/payload-info/' + i.name, i.name, 0, 0, i.name.length*7, 50,
              'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + payloadColor[i.type] +
              ';fontColor=#333333;strokeWidth=3;');
            edges.push(graph.insertEdge(parent, null, null, p, ii, edgeStyle));
          }
        }

        graph.addListener(mxEvent.CLICK, (sender, evt) => {
          const cell = evt.getProperty('cell'); // Get the cell that was clicked
          if (cell?.id) {
            return this.navController.navigateForward(cell.id);
          }
        });


      } finally {
        graph.getModel().endUpdate();
      }

      const layout = new mxHierarchicalLayout(graph, 'west');
      layout.traverseAncestors = false;
      layout.execute(parent);

      for (const e of edges) {
        const state = graph.view.getState(e);
        state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
      }


      graphCenterFit(graph, container);

    }, 500);


  }
}
