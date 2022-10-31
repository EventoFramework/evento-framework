import {Component, OnInit} from '@angular/core';
import graphviz from 'graphviz-wasm';
import {digraph} from 'graphviz-builder';
import {HandlerService} from "../../services/handler.service";
import * as svgPanZoom from "svg-pan-zoom";

@Component({
  selector: 'app-application-map',
  templateUrl: './application-map.page.html',
  styleUrls: ['./application-map.page.scss'],
})
export class ApplicationMapPage implements OnInit {
  svg: string;

  constructor(private handlerService: HandlerService) {
  }

  async ngOnInit() {
    const handlers = await this.handlerService.findAll();

    const bundles = {};

    for (let handler of handlers) {
      if (!bundles[handler.bundleName]) {
        bundles[handler.bundleName] = {
          components: {}
        }
      }
      if (!bundles[handler.bundleName].components[handler.componentName]) {
        bundles[handler.bundleName].components[handler.componentName] = {
          handlers: {},
          componentType: handler.componentType
        }
      }

      bundles[handler.bundleName].components[handler.componentName].handlers[handler.handledPayload.name] = {
        messageType: handler.handledPayload.type,
        handlerType: handler.handlerType,
        returnType: handler.returnType,
        returnIsMultiple: handler.returnIsMultiple,
        uuid: handler.uuid
      }
    }

    for (let handler of handlers) {

      const h = bundles[handler.bundleName].components[handler.componentName].handlers[handler.handledPayload.name];
      h.responseHandeledBy = [];
      h.invoke = [];
      if (handler.returnType) {
        for (const target of handlers.filter(h => h.handledPayload.name === handler.returnType.name && h.handlerType !== 'EventSourcingHandler')) {
          h.responseHandeledBy.push(target.uuid)
        }
      }
      for(const invocation of handler.invocations){
        for (const target of handlers.filter(h => h.handledPayload.name === invocation.name && h.handlerType !== 'EventSourcingHandler')) {
          h.invoke.push(target.uuid)
        }
      }
      //g.addNode()
    }

    console.log(bundles)
    const node = {
      name: "root",
      children: []
    }
    for (const bundle in bundles) {
      const bundleNode = {
        name: bundle,
        children: [],
      }
      for (const component in bundles[bundle].components) {
        const componentNode = {
          name: component,
          componentType: bundles[bundle].components[component].componentType,
          children: []
        }
        for (const handler in bundles[bundle].components[component].handlers) {
          const h = bundles[bundle].components[component].handlers[handler];
          if (h.handlerType === 'EventSourcingHandler') {
            continue;
          }
          const handlerNode = {
            name: handler,
            value: h.responseHandeledBy.length + h.invoke.length + 1,
            handler: h
          }
          componentNode.children.push(handlerNode);
        }
        bundleNode.children.push(componentNode)
      }
      node.children.push(bundleNode)
    }

    if(true) {

    }else {

      await graphviz.loadWASM();

      const g = digraph('G');
      g.set("rankdir", "LR")
      g.set("pad", "0.5")
      g.set("ranksep", "2")
      for (const bundle in bundles) {
        const bundleCluster = g.addCluster("cluster__" + bundle);
        bundleCluster.set("label", bundle)
        for (const component in bundles[bundle].components) {
          const componentCluster = bundleCluster.addCluster("cluster__" + component);
          for (const handler in bundles[bundle].components[component].handlers) {
            componentCluster.set("label", component)
            const h = bundles[bundle].components[component].handlers[handler];
            if (h.handlerType === 'EventSourcingHandler') {
              continue;
            }
            componentCluster.addNode(h.uuid, {label: handler, shape: 'circle'})
          }
        }
      }

      for (let handler of handlers) {

        if (handler.returnType) {
          for (const target of handlers.filter(h => h.handledPayload.name === handler.returnType.name && h.handlerType !== 'EventSourcingHandler')) {
            g.addEdge(handler.uuid, target.uuid)
          }
        }
        for (const invocation of handler.invocations) {
          for (const target of handlers.filter(h => h.handledPayload.name === invocation.name && h.handlerType !== 'EventSourcingHandler')) {
            g.addEdge(handler.uuid, target.uuid)
          }
        }
        //g.addNode()
      }

      const dot = g.to_dot();

      console.log(dot)
      this.svg = graphviz.layout(dot);
      document.getElementById("graph").innerHTML = this.svg;
      var panZoomTiger = svgPanZoom(document.getElementById("graph").children.item(0) as any, {
        contain: true
      })
    }
  }
}
