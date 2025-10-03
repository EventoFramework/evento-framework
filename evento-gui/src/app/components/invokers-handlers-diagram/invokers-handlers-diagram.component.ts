import {Component, ElementRef, Input, OnInit, ViewChild} from '@angular/core';
import {componentColor, graphCenterFit, payloadColor} from '../../services/utils';
import {NavController} from '@ionic/angular';
import {setZoom} from "../common";

declare const mxGraph: any;
declare const mxConstants: any;
declare const mxUtils: any;
declare const mxEvent: any;
declare const mxHierarchicalLayout: any;
declare const mxOrthogonalLayout: any;


@Component({
  selector: 'app-invokers-handlers-diagram',
  templateUrl: './invokers-handlers-diagram.component.html',
  styleUrls: ['./invokers-handlers-diagram.component.scss'],
})
export class InvokersHandlersDiagramComponent implements OnInit {

  @Input()
  payload: any;

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


    mxEvent.disableContextMenu(container);
    setZoom(container, graph)

    const edges = [];
    const edgeStyle = 'edgeStyle=elbowEdgeStyle;rounded=1;jumpStyle=arc;orthogonalLoop=1;jettySize=auto;html=1;dashed=1;' +
      'endArrow=block;endFill=1;orthogonal=1;strokeColor=#999999;strokeWidth=1;';

    graph.view.addListener(mxEvent.AFTER_RENDER, () => {
      for (const e of edges) {
        const state = graph.view.getState(e);
        state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
      }
    });

    setTimeout(() => {

      graph.getModel().beginUpdate();
      try {


        const p = graph.insertVertex(parent,'/payload-info/'+  this.payload.name, this.payload.name, 0, 0, this.payload.name.length*10, 50,
          'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + payloadColor[this.payload.type] +
          ';fontColor=' + payloadColor[this.payload.type] + ';strokeWidth=4;fontStyle=1;fontSize=14');

        for (const r of this.payload.returnedBy) {
          const l = graph.insertVertex(parent,'/component-info/'+  r.name, r.name, 0, 0,  r.name.length*7, 50,
            'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + componentColor[r.type] +
            ';fontColor=#333333;strokeWidth=3;');
          l.handler = r;
          edges.push(graph.insertEdge(parent, null, null, l, p, edgeStyle));
        }

        for (const i of this.payload.invokers) {
          const l = graph.insertVertex(parent,'/component-info/'+  i.name, i.name, 0, 0,  i.name.length*7, 50,
            'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + componentColor[i.type] +
            ';fontColor=#333333;strokeWidth=3;');
          edges.push(graph.insertEdge(parent, null, null, l, p, edgeStyle));
        }

        for (const s of this.payload.subscribers) {
          const l = graph.insertVertex(parent,'/component-info/'+ s.name, s.name, 0, 0, s.name.length*7, 50,
            'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + componentColor[s.type] +
            ';fontColor=#333333;strokeWidth=3;');
          l.handler = s;
          edges.push(graph.insertEdge(parent, null, null, p, l, edgeStyle));
        }


      } finally {
        graph.getModel().endUpdate();
      }

      // Configures automatic expand on mouseover
      graph.popupMenuHandler.autoExpand = true;
      // Installs a popupmenu handler using local function (see below).
      graph.popupMenuHandler.factoryMethod = (menu, cell, evt) => {
        if(cell?.vertex){
          const t = cell.handler;
          if (t) {
            if (t.path) {
              menu.addItem('Open Repository (' + t.line + ')', '',
                () => {
                  window.open(t.path + '#' + this.payload.linePrefix + t.line, '_blank');
                });
            }
          }
        }
      }

      graph.addListener(mxEvent.DOUBLE_CLICK, (sender, evt) => {
        const cell = evt.getProperty('cell'); // Get the cell that was clicked
        if (cell?.id) {
          return this.navController.navigateForward(cell.id);
        }
      });

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
