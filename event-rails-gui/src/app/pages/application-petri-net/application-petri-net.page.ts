import {Component, OnInit} from '@angular/core';
import {HandlerService} from "../../services/handler.service";

declare var mxGraph: any;
declare var mxHierarchicalLayout: any;

@Component({
  selector: 'app-application-petri-net',
  templateUrl: './application-petri-net.page.html',
  styleUrls: ['./application-petri-net.page.scss'],
})
export class ApplicationPetriNetPage implements OnInit {

  constructor(private handlerService: HandlerService) {
  }

  async ngOnInit() {

    const network = await this.handlerService.getPetriNet();
    console.log(network);
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
      const postStyle = "shape=ellipse;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;fillColor=transparent"
      if(showPosts) {
        for (const post of network.posts) {
          postNodes[post.id] = graph.insertVertex(parent, post.id, post.name, null, null, post.name.length * 7, 50, postStyle);
        }
      }
      const transitionStyle = "shape=rectangle;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;fillColor=transparent"
      for (const transition of network.transitions) {
        transitionNodes[transition.id] = graph.insertVertex(parent, transition.id, transition.name, null, null, transition.name.length * 7, 50, transitionStyle);
      }

      if(showPosts) {
        for (const post of network.posts) {
          for (const target of post.target) {
            graph.insertEdge(parent, null, '', postNodes[post.id], transitionNodes[target]);
          }
        }
      }

      for (const transition of network.transitions) {
        for (const target of transition.target) {
          if(showPosts) {
            graph.insertEdge(parent, null, '', transitionNodes[transition.id], postNodes[target]);
          }else{
            const targetTransition = network.posts.filter(p => p.id === target)[0];
            if(targetTransition) {
              for (const targetTarget of targetTransition.target) {
                if(transition.id !== targetTarget) {
                  graph.insertEdge(parent, null, '', transitionNodes[transition.id], transitionNodes[targetTarget]);
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
