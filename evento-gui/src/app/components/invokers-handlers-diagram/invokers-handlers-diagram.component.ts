import {Component, Input, OnInit} from '@angular/core';

declare const mxGraph: any;
declare const mxConstants: any;
declare const mxEvent: any;
declare const mxHierarchicalLayout: any;
declare const mxOrthogonalLayout: any;

export const componentColor = {
  'Aggregate': 'blue',
  'Service': 'red',
  'Projection': 'green',
  'Projector': 'lightgreen',
  'Saga': 'purple',
  'Observer': 'black',
  'Invoker': 'grey',
}
export const payloadColor = {
  'DomainCommand': '#3399fe',
  'ServiceCommand': '#ff68b9',
  'DomainEvent': '#ff992a',
  'ServiceEvent': '#cb3234',
  'View': '#5fc08b',
  'Invocation': 'grey',
  'Query': 'gold',
}

@Component({
  selector: 'app-invokers-handlers-diagram',
  templateUrl: './invokers-handlers-diagram.component.html',
  styleUrls: ['./invokers-handlers-diagram.component.scss'],
})
export class InvokersHandlersDiagramComponent implements OnInit {

  @Input()
  payload: any

  constructor() {
  }

  ngOnInit() {

    const container = <HTMLElement>document.getElementById('map');

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

    graph.view.addListener(mxEvent.AFTER_RENDER, function()
    {
      for(const e of edges){
        var state = graph.view.getState(e);
        state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
      }
    });

    graph.view.addListener(mxEvent.DOME, function()
    {
      console.log('done');
    });

    graph.getModel().beginUpdate();
    try {



      const p = graph.insertVertex(parent, this.payload.name, this.payload.name, 0, 0, 250, 50,
        'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor='+payloadColor[this.payload.type]+';fontColor='+payloadColor[this.payload.type]+';strokeWidth=4;fontStyle=1;fontSize=14');

      for (const r of this.payload.returnedBy) {
        console.log(r)
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
        console.log(s)
        const l = graph.insertVertex(parent, s.name, s.name, 0, 0, 200, 50, 'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor='+componentColor[s.type]+';fontColor=#333333;strokeWidth=3;');
        edges.push(graph.insertEdge(parent, null, null, p, l, edgeStyle));
      }


    } finally {
      graph.getModel().endUpdate();
    }

    const layout = new mxHierarchicalLayout(graph, 'west');
    layout.traverseAncestors = false;
    layout.execute(parent);

    for(const e of edges){
      var state = graph.view.getState(e);
      state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
    }



    /*


    const bounds = graph.getGraphBounds();
    const width = bounds.width;
    const height = bounds.height;
    const x = (graph.container.clientWidth - width) / 2;
    const y = (graph.container.clientHeight - height) / 2;
    console.log(graph.container.clientWidth );
    console.log(width );
    var canvas = container.getElementsByTagName('svg')[0];
    console.log(canvas)
    console.log(canvas.style['min-width'].replace('px',''))
    console.log(canvas.style['min-height'].replace('px',''));
    console.log(bounds);
    console.log(graph.container.clientHeight);
    graph.view.setTranslate(0,0);*/

  }

}
