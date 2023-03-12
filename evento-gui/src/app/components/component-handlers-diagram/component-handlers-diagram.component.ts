import {Component, ElementRef, Input, OnInit, ViewChild} from '@angular/core';
import {componentColor, payloadColor} from "../../services/utils";
import {NavController} from "@ionic/angular";

declare const mxGraph: any;
declare const mxConstants: any;
declare const mxEvent: any;
declare const mxHierarchicalLayout: any;
declare const mxOrthogonalLayout: any;

@Component({
  selector: 'app-component-handlers-diagram',
  templateUrl: './component-handlers-diagram.component.html',
  styleUrls: ['./component-handlers-diagram.component.scss'],
})
export class ComponentHandlersDiagramComponent implements OnInit {

  @Input()
  component;


  @ViewChild('container', {static: true}) container: ElementRef;

  constructor(private navController: NavController) {
  }

  ngOnInit() {


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
    const edgeStyle = 'edgeStyle=elbowEdgeStyle;rounded=1;jumpStyle=arc;orthogonalLoop=1;jettySize=auto;html=1;dashed=1;endArrow=block;endFill=1;orthogonal=1;strokeColor=#999999;strokeWidth=1;';

    graph.view.addListener(mxEvent.AFTER_RENDER, function () {
      for (const e of edges) {
        var state = graph.view.getState(e);
        state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
      }
    });

    graph.getModel().beginUpdate();
    try {

      for (let h of this.component.handlers) {
        if(h.handlerType === 'EventSourcingHandler'){
          continue;
        }
        const p = graph.insertVertex(parent, null, this.component.componentName, 0, 0, 250, 50,
          'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + componentColor[this.component.componentType] + ';fontColor=' + componentColor[this.component.componentType] + ';strokeWidth=4;fontStyle=1;fontSize=14');

        const t = graph.insertVertex(parent, h.handledPayload.name, h.handledPayload.name, 0, 0, 250, 50,
          'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + payloadColor[h.handledPayload.type] + ';fontColor=#333333;strokeWidth=3;');
        edges.push(graph.insertEdge(parent, null, null, t, p, edgeStyle));

        if(h.returnType){
          const r = graph.insertVertex(parent, h.returnType.name, h.returnType.name  + (h.returnIsMultiple ? '[]' : ''), 0, 0, 250, 50,
            'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + payloadColor[h.returnType.type] + ';fontColor=#333333;strokeWidth=3;');
          edges.push(graph.insertEdge(parent, null, null, p, r, edgeStyle));
        }


        for(let i of Object.values(h.invocations) as any[]){
          const ii = graph.insertVertex(parent, i.name, i.name, 0, 0, 250, 50,
            'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + payloadColor[i.type] + ';fontColor=#333333;strokeWidth=3;');
          edges.push(graph.insertEdge(parent, null, null, p, ii, edgeStyle));
        }
      }

      graph.addListener(mxEvent.CLICK, (sender, evt) => {
        const cell = evt.getProperty('cell'); // Get the cell that was clicked
        if (cell?.id){
          return this.navController.navigateForward('/payload-info/' + cell.id);
        }
      });


      /*
      const p = graph.insertVertex(parent, this.payload.name, this.payload.name, 0, 0, 250, 50,
        'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor='+payloadColor[this.payload.type]+';fontColor='+payloadColor[this.payload.type]+';strokeWidth=4;fontStyle=1;fontSize=14');

      for (const r of this.payload.returnedBy) {
        const l = graph.insertVertex(parent, r.name, r.name, 0, 0, 200, 50,
          'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor='+componentColor[r.type]+';fontColor=#333333;strokeWidth=3;');
        edges.push(graph.insertEdge(parent, null, null, l, p, edgeStyle));
      }

      for (const i of this.payload.invokers) {
        const l = graph.insertVertex(parent, i.name, i.name, 0, 0, 200, 50,
          'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor='+componentColor[i.type]+';fontColor=#333333;strokeWidth=3;');
        edges.push(graph.insertEdge(parent, null, null, l, p, edgeStyle));
      }

      for (const s of this.payload.subscribers) {
        const l = graph.insertVertex(parent, s.name, s.name, 0, 0, 200, 50, 'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor='+componentColor[s.type]+';fontColor=#333333;strokeWidth=3;');
        edges.push(graph.insertEdge(parent, null, null, p, l, edgeStyle));
      }
*/

    } finally {
      graph.getModel().endUpdate();
    }

    const layout = new mxHierarchicalLayout(graph, 'west');
    layout.traverseAncestors = false;
    layout.execute(parent);

    for (const e of edges) {
      var state = graph.view.getState(e);
      state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
    }


    setTimeout(() => {
      const bounds = graph.getGraphBounds();
      const width = bounds.width;
      const height = bounds.height;
      const x = (graph.container.clientWidth - width) / 2;
      const y = (graph.container.clientHeight - height) / 2;
      graph.view.setTranslate(x, y);
    }, 100);


  }

}
