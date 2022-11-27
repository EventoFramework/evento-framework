import {Component, OnInit} from '@angular/core';
import {HandlerService} from "../../services/handler.service";
import {BundleColorService} from "../../services/bundle-color.service";

declare var mxGraph: any;
declare var mxConstants: any;
declare var mxUtils: any;

@Component({
  selector: 'evento-app-map-diagram',
  templateUrl: './app-map-diagram.component.html',
  styleUrls: ['./app-map-diagram.component.scss'],
})
export class AppMapDiagramComponent implements OnInit {

  padding = 20;

  constructor(private handlerService: HandlerService,
              private bundleColorService: BundleColorService) {
  }

  private diameter(hull) {
    const c = this.getCenter(hull);
    let maxD = 0;
    for (let h of hull) {
      var d = ((((h.x - c.x) ** 2 + (h.y - c.y) ** 2) ** 0.5) + h.r) * 2;
      if (d > maxD) {
        maxD = d;
      }
    }
    return maxD;
  }

  private intersect(x1, y1, x2, y2, r1, r2) {
    const d = ((x1 - x2) * (x1 - x2)
      + (y1 - y2) * (y1 - y2)) ** 0.5;
    if (d <= r1 - r2) {
      return true;
    } else if (d <= r2 - r1) {
      return true;
    } else if (d + 0.1 < r1 + r2) {
      return true;
    }
    return false;
  }

  private intersectAny(x1, y1, r1, circles) {
    for (let c of circles) {
      if (this.intersect(c.x, c.y, x1, y1, c.r, r1)) {
        return true;
      }
    }
    return false;
  }

  private getCenter(points) {

    const makeHullPresorted = (points) => {
      if (points.length <= 1)
        return points.slice();
      // Andrew's monotone chain algorithm. Positive y coordinates correspond to "up"
      // as per the mathematical convention, instead of "down" as per the computer
      // graphics convention. This doesn't affect the correctness of the result.
      let upperHull = [];
      for (let i = 0; i < points.length; i++) {
        const p = points[i];
        while (upperHull.length >= 2) {
          const q = upperHull[upperHull.length - 1];
          const r = upperHull[upperHull.length - 2];
          if ((q.x - r.x) * (p.y - r.y) >= (q.y - r.y) * (p.x - r.x))
            upperHull.pop();
          else
            break;
        }
        upperHull.push(p);
      }
      upperHull.pop();
      let lowerHull = [];
      for (let i = points.length - 1; i >= 0; i--) {
        const p = points[i];
        while (lowerHull.length >= 2) {
          const q = lowerHull[lowerHull.length - 1];
          const r = lowerHull[lowerHull.length - 2];
          if ((q.x - r.x) * (p.y - r.y) >= (q.y - r.y) * (p.x - r.x))
            lowerHull.pop();
          else
            break;
        }
        lowerHull.push(p);
      }
      lowerHull.pop();
      if (upperHull.length == 1 && lowerHull.length == 1 && upperHull[0].x == lowerHull[0].x && upperHull[0].y == lowerHull[0].y)
        return upperHull;
      else
        return upperHull.concat(lowerHull);
    }

    let newPoints = points.slice();
    newPoints.sort((a, b) => {
      if (a.x < b.x)
        return -1;
      else if (a.x > b.x)
        return +1;
      else if (a.y < b.y)
        return -1;
      else if (a.y > b.y)
        return +1;
      else
        return 0;
    });
    const hull = makeHullPresorted(newPoints);
    let mx = 0;
    let my = 0;
    let ra = 0;
    for (let c of hull) {
      var w = c.r ** (hull.length - 1) //((c.r*2) ** 2)
      mx += c.x * w
      my += c.y * w
      ra += w
    }
    return {x: mx / ra, y: my / ra};
  }

  private makeThirdVertex(x2, y2, x3, y3, d1, d3, firstSolution) {
    let d2 = ((x3 - x2) ** 2 + (y3 - y2) ** 2) ** 0.5;
    let k = (d2 ** 2 + d1 ** 2 - d3 ** 2) / (2 * d2);
    let h = (d1 ** 2 - k ** 2) ** 0.5;
    if (!h) {
      return null;
    }
    return firstSolution ? [x2 + (k / d2) * (x3 - x2) - (h / d2) * (y3 - y2),
      y2 + (k / d2) * (y3 - y2) + (h / d2) * (x3 - x2)] : [x2 + (k / d2) * (x3 - x2) + (h / d2) * (y3 - y2), y2 + (k / d2) * (y3 - y2) - (h / d2) * (x3 - x2)];

    //vertex_1b(1) = x2 + (k/d2)*(x3 - x2) + (h/d2)*(y3 - y2);
    //vertex_1b(2) = y2 + (k/d2)*(y3 - y2) - (h/d2)*(x3 - x2);
  }

  private addCircle(circles, radius, id, name) {
    var center = this.getCenter(circles);

    var minD = null;
    var c1 = null;
    var c2 = null;
    var p3 = [0, 0];
    if (circles.length === 1) {
      p3 = [0, radius + circles[0].r]
    }

    for (let c of circles) {
      for (let n of circles) {
        if (c === n) {
          continue;
        }
        var p = this.makeThirdVertex(c.x, c.y, n.x, n.y,
          c.r + radius,
          n.r + radius,
          true
        )
        if (!p) {
          continue;
        }
        if (this.intersectAny(p[0], p[1], radius, circles)) {
          p = this.makeThirdVertex(c.x, c.y, n.x, n.y,
            c.r + radius,
            n.r + radius,
            false
          )
        }
        if (this.intersectAny(p[0], p[1], radius, circles)) {
          continue;
        }
        var d = ((p[0] - center.x) ** 2 + (p[1] - center.y) ** 2) ** 0.5;
        if (minD == null || d < minD) {
          minD = d;
          p3 = p;
          c1 = c;
          c2 = n
        }

      }
    }

    let c3 = {
      x: p3[0],
      y: p3[1],
      r: radius,
      n: name,
      id
    }

    circles.push(c3)


  }

  async ngOnInit() {
    const handlers = await this.handlerService.findAll();

    const priority = ['Aggregate','Service','Projector','Projection','Saga','Invoker']
    handlers.sort((a,b) => {
      return priority.indexOf(a.componentType) - priority.indexOf(b.componentType)
    })
    var minCircleSize = 0;

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
      if (handler.handledPayload.name.length > minCircleSize) {
        minCircleSize = handler.handledPayload.name.length;
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
        for (const target of handlers.filter(h => h.handledPayload.name === handler.returnType.name && h.handlerType !== 'EventSourcingHandler')) {
          h.responseHandeledBy.push(target.uuid)
        }
      }
      for (const invocation of handler.invocations) {
        for (const target of handlers.filter(h => h.handledPayload.name === invocation.name && h.handlerType !== 'EventSourcingHandler')) {
          h.invoke.push(target.uuid)
        }
      }
      handler.h = h;
    }

    const bundleComponentCircles = {};
    const componentHandlerCircles = {};

    var fontSize = 1;

    const bundleCircles = [];
    for (const bundle in bundles) {
      const componentCircles = [];
      for (const component in bundles[bundle].components) {
        const handlerCircles = [];
        for (const handler in bundles[bundle].components[component].handlers) {
          const h = bundles[bundle].components[component].handlers[handler];
          if (h.handlerType === 'EventSourcingHandler') {
            continue;
          }
          this.addCircle(handlerCircles, (60) + (10 * (h.invoke.length + h.responseHandeledBy.length)), h.uuid, handler);
        }
        componentHandlerCircles[component] = handlerCircles;
        this.addCircle(componentCircles, (this.diameter(handlerCircles) / 2) + this.padding, component, component)
      }
      bundleComponentCircles[bundle] = componentCircles;
      this.addCircle(bundleCircles, (this.diameter(componentCircles) / 2) + this.padding, bundle, bundle)
    }
    console.log(bundleCircles);

    const container = <HTMLElement>document.getElementById('graph');

    const graph = new mxGraph(container)
    const parent = graph.getDefaultParent();
    graph.centerZoom = false;
    graph.setTooltips(false);
    graph.setEnabled(false);

    // Enables panning with left mouse button
    graph.panningHandler.useLeftButtonForPanning = true;
    graph.panningHandler.ignoreCell = true;
    graph.container.style.cursor = 'move';
    graph.setPanning(true);
    graph.resizeContainer = true;

    container.addEventListener('wheel', (e: any) => {
        e.preventDefault();
        e.stopPropagation();
        if(e.wheelDelta > 0){
          graph.zoomIn();
        }else{
          graph.zoomOut();
        }
    })

    graph.getModel().beginUpdate()
    try {

      const nodeStyle = "shape=ellipse;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;fillColor=white;"
      var diameter = this.diameter(bundleCircles);

      const minX = Math.min.apply(null, bundleCircles.map(c => c.x - c.r));
      const minY = Math.min.apply(null, bundleCircles.map(c => c.y - c.r));
      for (let c of bundleCircles) {
        var x = c.x - c.r - minX;
        var y = c.y - c.r - minY;
        var bp = graph.insertVertex(parent, null, c.n,
          x,
          y,
          c.r * 2,
          c.r * 2, nodeStyle + "verticalAlign=top;labelBackgroundColor=#ffffff;labelBorderColor="+this.bundleColorService.getColorForBundle(c.id)+";spacingTop=-3;strokeColor="+this.bundleColorService.getColorForBundle(c.id));
        bp.setConnectable(false);

        const _minX = Math.min.apply(null, bundleComponentCircles[c.id].map(h => h.x - h.r));
        const _minY = Math.min.apply(null, bundleComponentCircles[c.id].map(h => h.y - h.r));

        var _diameter = c.r * 2;
        var _centralPoint = this.getCenter(bundleComponentCircles[c.id]);
        const _center = (_diameter / 2);

        for (let _c of bundleComponentCircles[c.id]) {
          var _x = _c.x - _c.r - _minX;
          var _y = _c.y - _c.r - _minY;
          var _bp = graph.insertVertex(bp, null, _c.n,
            _x + (_center - (_centralPoint.x - _minX)),
            _y + (_center - (_centralPoint.y - _minY)),
            _c.r * 2,
            _c.r * 2, nodeStyle + ";verticalAlign=top;fillColor=white;verticalAlign=top;labelBackgroundColor=#ffffff;spacingTop=3;");
          _bp.setConnectable(false);

          const __minX = Math.min.apply(null, componentHandlerCircles[_c.id].map(h => h.x - h.r));
          const __minY = Math.min.apply(null, componentHandlerCircles[_c.id].map(h => h.y - h.r));

          var __diameter = _c.r * 2;
          var __centralPoint = this.getCenter(componentHandlerCircles[_c.id]);
          const __center = (__diameter / 2);

          for (let __c of componentHandlerCircles[_c.id]) {
            var __x = __c.x - __c.r - __minX;
            var __y = __c.y - __c.r - __minY;
            var __bp = graph.insertVertex(_bp, __c.id, __c.n,
              __x + (__center - (__centralPoint.x - __minX)),
              __y + (__center - (__centralPoint.y - __minY)),
              __c.r * 2,
              __c.r * 2, nodeStyle + ";fillColor=transparent");
            __bp.setConnectable(true);
          }

        }
      }


      const edgeStyle = "opacity=10;strokeColor=#000000;"
      for (let handler of handlers) {
        for (let to of handler.h.invoke) {
          graph.insertEdge(parent, null, null, graph.getModel().getCell(handler.uuid), graph.getModel().getCell(to), edgeStyle)
        }
        for (let to of handler.h.responseHandeledBy) {
          graph.insertEdge(parent, null, null, graph.getModel().getCell(handler.uuid), graph.getModel().getCell(to), edgeStyle + "dashed=1;")
        }
      }

    } finally {
      graph.getModel().endUpdate()
    }


    const updateStyle = (state, hover) => {
      var q = [state];
      while (q.length > 0) {
        var n = q.shift();
        if (n.cell.edges) {
          for (const e of n.cell.edges) {
            if (e.source === n.cell) {
              const eState = graph.view.getState(e);
              eState.style['opacity'] = hover ? 100 : 10
              eState.shape.apply(eState);
              eState.shape.redraw();
              q.push(graph.view.getState(e.target))
            }
          }
        }
        n.style[mxConstants.STYLE_STROKEWIDTH] = (hover) ? '4' : '1';
        n.shape.apply(n);
        n.shape.redraw();
      }


    };

    graph.addMouseListener(
      {
        currentState: null,
        previousStyle: null,
        mouseDown: function (sender, me) {
          if (this.currentState != null) {
            this.dragLeave(me.getEvent(), this.currentState);
            this.currentState = null;
          }
        },
        mouseMove: function (sender, me) {
          if (this.currentState != null && me.getState() == this.currentState) {
            return;
          }

          var tmp = graph.view.getState(me.getCell());

          // Ignores everything but vertices
          if (graph.isMouseDown || (tmp != null && !
            graph.getModel().isVertex(tmp.cell))) {
            tmp = null;
          }

          if (tmp != this.currentState) {
            if (this.currentState != null) {
              this.dragLeave(me.getEvent(), this.currentState);
            }

            this.currentState = tmp;

            if (this.currentState != null) {
              this.dragEnter(me.getEvent(), this.currentState);
            }
          }
        },
        mouseUp: function (sender, me) {
        },
        dragEnter: function (evt, state) {
          if (state != null) {

            updateStyle(state, true);
          }
        },
        dragLeave: function (evt, state) {
          if (state != null) {
            updateStyle(state, false);
            /*
            state.style = this.previousStyle;
            updateStyle(state, false);
            state.shape.apply(state);
            state.shape.redraw();

            if (state.text != null)
            {
              state.text.apply(state);
              state.text.redraw();
            }*/
          }
        }
      });

  }

}
