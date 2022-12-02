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
  private showPosts = false;


  constructor(private handlerService: HandlerService,
              private bundleColorService: BundleColorService,
              private route: ActivatedRoute) {
  }



  async ngOnInit() {

    const handlerId = this.route.snapshot.params.handlerId;

    const network = handlerId === 'all' ? await this.handlerService.getPetriNet() : await this.handlerService.getPetriNet(handlerId) ;
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


    console.log(network);

    const layout = new mxHierarchicalLayout(graph, 'west');
    graph.getModel().beginUpdate();
    try {
      const postNodes = {};
      const transitionNodes = {};
      const transitionRef = {};
      const postStyle = 'shape=ellipse;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;fillColor=transparent';
      if (this.showPosts) {
        for (const post of network.posts) {
          postNodes[post.id] = graph.insertVertex(parent, post.id, post.action, null, null, 50, 50, postStyle);
        }
      }
      const transitionStyle = 'shape=rectangle;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;';
      for (const transition of network.transitions) {
        if (transition.action === 'Sink') {continue;}
        transition.name = transition.bundle + '\n\n' + transition.component + '\n\n' + transition.action;
        Math.max(transition.bundle.length, transition.component.length, transition.action.length);
        let additionalStyles = 'fillColor=' + this.bundleColorService.getColorForBundle(transition.bundle) + ';';
        const width = Math.max(transition.bundle.length, transition.component.length, transition.action.length) * 10;
        let height = 90;
        let text = transition.name;
        if (transition.meanServiceTime) {
          text += '\n\nmst: ' + transition.meanServiceTime.toFixed(3) + ' [ms]';
          height += 30;
        }
        if (transition.bundle === 'event-store' || transition.component === 'SagaStore' || transition.component === 'ProjectorStore') {
          additionalStyles += 'shape=cylinder;verticalAlign=bottom;spacingBottom=20;';
          height += 70;
        }
        transitionNodes[transition.id] = graph.insertVertex(parent, transition.id, text, null, null, width,
          height,
          transitionStyle + additionalStyles);
        transitionRef[transition.id] = transition;
      }


      const edgeStyle = 'endArrow=classic;html=1;labelBackgroundColor=white;elbow=vertical;edgeStyle=orthogonalEdgeStyle;curved=1;';

      if (this.showPosts) {
        for (const post of network.posts) {
          for (const target of post.target) {
            graph.insertEdge(parent, null, '', postNodes[post.id], transitionNodes[target], edgeStyle);
          }
        }
      }

      for (const transition of network.transitions) {
        if (this.showPosts) {
          for (const target of transition.target) {
              graph.insertEdge(parent, null, '', transitionNodes[transition.id], postNodes[target], edgeStyle);
            }
        }else{
          const transitions = [];
          for (const target of transition.target) {
            const targetTransition = network.posts.filter(p => p.id === target && p.action !== 'Token')[0];
            if (targetTransition) {
              for (const targetTarget of targetTransition.target) {
                if (transition.id !== targetTarget && transitionNodes[targetTarget]) {
                  transitions.push(transitionRef[targetTarget]);
                  }
              }
            }

          }
          for(const target of transitions.sort((a,b) => a?.async - b?.async)){
            let txt = '';
            if(target.meanServiceTime && target.meanThroughput){
              txt = (target.meanServiceTime*target.meanThroughput).toFixed(3) +' [r]';
            }
            graph.insertEdge(parent, null, txt, transitionNodes[transition.id],
              transitionNodes[target.id], edgeStyle +';'+ (target.async ? 'dashed=1' : 'dashed=0'));
          }
        }
      }

      // Executes the layout
      layout.execute(parent);


    } finally {
      graph.getModel().endUpdate();
    }
  }


  togglePosts($event: any) {
    this.showPosts = $event.detail.checked;
    return this.ngOnInit();
  }
}
