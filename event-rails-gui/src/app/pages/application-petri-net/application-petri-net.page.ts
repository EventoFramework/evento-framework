import {Component, OnInit} from '@angular/core';
import {HandlerService} from "../../services/handler.service";
import {BundleColorService} from "../../services/bundle-color.service";
import {add} from "ionicons/icons";

declare var mxGraph: any;
declare var mxHierarchicalLayout: any;

@Component({
  selector: 'app-application-petri-net',
  templateUrl: './application-petri-net.page.html',
  styleUrls: ['./application-petri-net.page.scss'],
})
export class ApplicationPetriNetPage implements OnInit {


  constructor(private handlerService: HandlerService,
              private bundleColorService: BundleColorService) {
  }

  async ngOnInit() {

    const network = await this.handlerService.getPetriNet();
    const container = <HTMLElement>document.getElementById('graph');

    const graph = new mxGraph(container)
    const parent = graph.getDefaultParent()
    graph.setTooltips(true)

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
    })

    const showPosts = false;

    var layout = new mxHierarchicalLayout(graph, 'west');
    graph.getModel().beginUpdate()
    try {
      const postNodes = {}
      const transitionNodes = {}
      const transitionRef = {}
      const postStyle = "shape=ellipse;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;fillColor=transparent"
      if (showPosts) {
        for (const post of network.posts) {
          postNodes[post.id] = graph.insertVertex(parent, post.id, null, null, null, 50, 50, postStyle);
        }
      }
      const transitionStyle = "shape=rectangle;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;"
      for (const transition of network.transitions) {
        if (transition.action === 'Sink') continue;
        transition.name = transition.bundle + "\n\n" + transition.component + "\n\n" + transition.action
        Math.max(transition.bundle.length, transition.component.length, transition.action.length)
        let additionalStyles = "fillColor=" + this.bundleColorService.getColorForBundle(transition.bundle) + ";"
        let width = Math.max(transition.bundle.length, transition.component.length, transition.action.length) * 10;
        let height = 90;
        let text = transition.name;
        if (transition.meanServiceTime) {
          text += "\n\nmst: " + transition.meanServiceTime.toFixed(3) + " [s]"
          height += 30
        }
        if (transition.meanThroughput) {
          text += "\n\nthr: " + transition.meanThroughput.toFixed(3) + " [r/s]"
          height += 30
        }
        if (transition.bundle === 'event-store' || transition.component === 'SagaStore' || transition.component === 'ProjectorStore') {
          additionalStyles += "shape=cylinder;verticalAlign=bottom;spacingBottom=20;"
          height += 70
        }
        transitionNodes[transition.id] = graph.insertVertex(parent, transition.id, text, null, null, width,
          height,
          transitionStyle + additionalStyles);
        transitionRef[transition.id] = transition;
      }

      if (showPosts) {
        for (const post of network.posts) {
          for (const target of post.target) {
            graph.insertEdge(parent, null, '', postNodes[post.id], transitionNodes[target]);
          }
        }
      }

      const edgeStyle = "endArrow=classic;html=1;rounded=0;curved=1;labelBackgroundColor=white"
      for (const transition of network.transitions) {
        for (const target of transition.target) {
          if (showPosts) {
            graph.insertEdge(parent, null, '', transitionNodes[transition.id], postNodes[target], edgeStyle);
          } else {
            const targetTransition = network.posts.filter(p => p.id === target && p.action !== 'Token')[0];
            if (targetTransition) {
              for (const targetTarget of targetTransition.target) {
                if (transition.id !== targetTarget && transitionNodes[targetTarget]) {
                  const target  = transitionRef[targetTarget]
                  var txt = ''
                  if(target.meanServiceTime && target.meanThroughput){
                    txt = (target.meanServiceTime*target.meanThroughput).toFixed(3) +" [r]"
                  }
                  graph.insertEdge(parent, null, txt, transitionNodes[transition.id], transitionNodes[targetTarget], edgeStyle);
                }
              }
            }
          }
        }
      }

      // Executes the layout
      layout.execute(parent);


    } finally {
      graph.getModel().endUpdate()
    }
  }


}
