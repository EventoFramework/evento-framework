import {Component, OnInit} from '@angular/core';
import graphviz from 'graphviz-wasm';
import {digraph} from 'graphviz-builder';
import {HandlerService} from "../../services/handler.service";
import * as svgPanZoom from "svg-pan-zoom";

@Component({
  selector: 'app-application-graph',
  templateUrl: './application-graph-page.component.html',
  styleUrls: ['./application-graph-page.component.scss'],
})
export class ApplicationGraphPage implements OnInit {
  svg: string;

  constructor(private handlerService: HandlerService) {
  }

  async ngOnInit() {
    const handlers = await this.handlerService.findAll();

    const bundles = {};

    for (let handler of handlers) {
      if (!bundles[handler.bundleId]) {
        bundles[handler.bundleId] = {
          components: {}
        }
      }
      if (!bundles[handler.bundleId].components[handler.componentName]) {
        bundles[handler.bundleId].components[handler.componentName] = {
          handlers: {},
          componentType: handler.componentType
        }
      }

      bundles[handler.bundleId].components[handler.componentName].handlers[handler.handledPayload.name] = {
        messageType: handler.handledPayload.type,
        handlerType: handler.handlerType,
        returnType: handler.returnType,
        returnIsMultiple: handler.returnIsMultiple,
        uuid: handler.uuid
      }
    }

    for (let handler of handlers) {

      const h = bundles[handler.bundleId].components[handler.componentName].handlers[handler.handledPayload.name];
      h.responseHandeledBy = [];
      h.invoke = [];
      if (handler.returnType) {
        for (const target of Object.values<any>(handlers).filter(h => h.handledPayload.name === handler.returnType.name && h.handlerType !== 'EventSourcingHandler')) {
          h.responseHandeledBy.push(target.uuid)
        }
      }
      for(const invocation of Object.values<any>(handler.invocations)){
        for (const target of handlers.filter(h => h.handledPayload.name === invocation.name && h.handlerType !== 'EventSourcingHandler')) {
          h.invoke.push(target.uuid)
        }
      }
      //g.addNode()
    }
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
  }
}
