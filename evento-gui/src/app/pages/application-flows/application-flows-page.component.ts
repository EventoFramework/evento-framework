import {Component, OnInit} from '@angular/core';
import {HandlerService} from "../../services/handler.service";
import {BundleColorService} from "../../services/bundle-color.service";
import {add} from "ionicons/icons";
import * as mermaid from 'mermaid';

declare var mxGraph: any;
declare var mxHierarchicalLayout: any;

@Component({
  selector: 'app-application-petri-net',
  templateUrl: './application-flows-page.component.html',
  styleUrls: ['./application-flows-page.component.scss'],
})
export class ApplicationFlowsPage implements OnInit {


  constructor(private handlerService: HandlerService,
              private bundleColorService: BundleColorService) {
  }



  async ngOnInit() {


    const handlers = await this.handlerService.findAll();
    /*
    var handler = handlers.filter(h => h.handledPayload.name === 'DemoLifecycleAgent::action')[0];
    console.log(handler);




    /*
    const network = await this.handlerService.getPetriNet();
    const posts = {};
    for(const p of network.posts){
      posts[p.id] = p;
    }
    const transitions = {};
    for(const t of network.transitions){
      transitions[t.id] = t;
    }
    var handler = handlers.filter(h => h.handledPayload.name === 'DemoLifecycleAgent::action')[0];
    var diagram = "sequenceDiagram\n";
    const manageTransition = (c, t) => {
      console.log("t", t)
      const arrow = c == "EventStore" ? "-->>" : "->>"
      diagram += c+arrow+t.component+": "+t.action+"\n";
      for(const tt of t.target){
        console.log("p", posts[tt])
        if(posts[tt].action === 'Token') {
          continue;
        }
        if(posts[tt].action === 'Sink') {
          continue;
        }
        if(posts[tt].component === 'ProjectorStore') {
          continue;
        }
        if(posts[tt].component === 'SagaStore') {
          continue;
        }
        if(posts[tt].component === t.component && posts[tt].action === t.action){
          continue;
        }
        for(const pt of posts[tt].target){
          if(transitions[pt].component == 'Gateway'){
            for(const gp of transitions[pt].target){
              if(posts[gp].action === 'Token') {
                continue;
              }
              if(posts[gp].action === 'Sink') {
                continue;
              }
              if(posts[gp].component === 'ProjectorStore') {
                continue;
              }
              if(posts[gp].component === 'SagaStore') {
                continue;
              }
              if(posts[gp].component === t.component && posts[gp].action === t.action){
                continue;
              }
              for(const gpt of posts[gp].target){
                manageTransition(t.component, transitions[gpt]);
              }
            }
          }else {
            manageTransition(t.component, transitions[pt]);
          }
        }
      }
    }

    const t = network.transitions.filter(t => t.bundle == handler.bundleId && t.component == handler.componentName && t.action == handler.handledPayload.name)[0];

    manageTransition("Invoker", t);

    mermaid.default.initialize({});
    const container = <HTMLElement>document.getElementById('graph');
    console.log(diagram);
    mermaid.default.render("graphDiv", diagram, (svgCode) => {
      container.innerHTML = svgCode;
    });





    /*
    const generateSequence = (handler, parentComponent, previous, parent, sync) => {

      for(const i of handler.invocations){
        for(const h of handlers.filter(h => h.handledPayload.name === i.name)){
          previous += handler.componentName + "->>" + h.componentName + ": " + h.handledPayload.name + "\n"
          previous += generateSequence(h, handler.componentName, "", () => h.componentName + "->>" + handler.componentName + ": \n" +(sync ? handler.componentName + "->>" + parentComponent + ": \n" +  parent() : ""), true)
        }
      }
      if(["InvocationHandler", "CommandHandler", "AggregateCommandHandler", "ServiceCommandHandler", "QueryHandler"].includes(handler.handlerType) && sync) {
       previous += parent();
      }
      if(handler.returnType){
        for(const h of handlers.filter(h => h.handledPayload.name === handler.returnType.name && h.handlerType !== 'EventSourcingHandler')){
          previous += handler.componentName + "-->>" + h.componentName + ": " + h.handledPayload.name + "\n"
          previous += generateSequence(h, handler.componentName, "",() => "", false)
        }
      }

      return previous;
    }


    var handler = handlers.filter(h => h.handledPayload.name === 'DemoLifecycleAgent::action')[0];
    console.log(handler);
    const container = <HTMLElement>document.getElementById('graph');
    mermaid.default.initialize({});
    let graphDefinition = generateSequence(handler,"Invoker", "sequenceDiagram\nInvoker->>" + handler.componentName + ": " + handler.handledPayload.name + "\n",
      () => "", true)
    console.log(graphDefinition);
    mermaid.default.render("graphDiv", graphDefinition, (svgCode) => {
      container.innerHTML = svgCode;
    });
    /*
*/
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

    var layout = new mxHierarchicalLayout(graph, 'north');
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
          text += "\n\nmst: " + transition.meanServiceTime.toFixed(3) + " [ms]"
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
