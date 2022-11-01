import {Component, OnInit} from '@angular/core';
import {ApplicationMap} from "./model";
import {max, min} from "rxjs/operators";

declare var mxGraph: any;
declare var mxFastOrganicLayout: any;

@Component({
  selector: 'app-edged-circle-pack',
  templateUrl: './edged-circle-pack.component.html',
  styleUrls: ['./edged-circle-pack.component.scss'],
})
export class EdgedCirclePackComponent implements OnInit {

  constructor() {
  }

  private diameter(hull) {
    const c = this.getCenter(hull);
    let maxD = 0;
    console.log("diameter")
    for (let h of hull) {
      var d = ((((h.x - c.x) ** 2 + (h.y - c.y) ** 2) ** 0.5) + h.r) * 2;
      console.log(d)
      if (d > maxD) {
        maxD = d;
      }
    }
    console.log("d:", maxD)
    return maxD;
  }

  private intersect(x1, y1, x2, y2, r1, r2) {
    const d = ((x1 - x2) * (x1 - x2)
      + (y1 - y2) * (y1 - y2)) ** 0.5;
    if (d <= r1 - r2) {
      return true;
    } else if (d <= r2 - r1) {
      return true;
    } else if (d + 0.00000000000001 < r1 + r2) {
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
    const POINT_COMPARATOR = (a, b) => {
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
    }

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

    const makeHull = (points) => {
      let newPoints = points.slice();
      newPoints.sort(POINT_COMPARATOR);
      return makeHullPresorted(newPoints);
    }
    const hull = makeHull(points);
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

  ngOnInit() {


    const map: ApplicationMap = {
      bundles: [
        {
          id: "bundle1",
          name: "bundle1",
          components: [{
            id: "component1",
            name: "component1",
            handledMessages: [
              {
                id: "node1",
                name: "node1"
              }, {
                id: "nodeA",
                name: "nodeA"
              }
            ]
          }]
        },
        {
          id: "bundle2",
          name: "bundle2",
          components: [{
            id: "component2",
            name: "component2",
            handledMessages: [
              {
                id: "node3",
                name: "node3"
              },
              {
                id: "node4",
                name: "node4"
              },
              {
                id: "node5",
                name: "node5"
              },
              {
                id: "nodeF",
                name: "nodeF"
              }
            ]
          }, {
            id: "componentA",
            name: "componentA",
            handledMessages: [
              {
                id: "node3",
                name: "node3"
              },
              {
                id: "node4",
                name: "node4"
              },
              {
                id: "node5",
                name: "node5"
              },
              {
                id: "nodeF",
                name: "nodeF"
              }
            ]
          }]
        },
        {
          id: "bundle3",
          name: "bundle3",
          components: [{
            id: "component3",
            name: "component3",
            handledMessages: [
              {
                id: "node6",
                name: "node6"
              },
              {
                id: "nodeB",
                name: "nodeB"
              }]
          },
            {
              id: "component4",
              name: "component4",
              handledMessages: [
                {
                  id: "node7",
                  name: "node7"
                },
                {
                  id: "node8",
                  name: "node8"
                },
                {
                  id: "node9",
                  name: "node9"
                },
                {
                  id: "nodeC",
                  name: "nodeC"
                },
                {
                  id: "nodeD",
                  name: "nodeD"
                }]
            }]
        }
      ],
      edges: {
        node1: {
          links: ["node6", "nodeC"],
          k: 3
        },
        nodeF: {
          k: 1,
          links: ["node1"]
        },
        node6: {
          links: [],
          k:1
        },
        nodeC: {
          links: [],
          k:1
        }

      }
    }


    const container = <HTMLElement>document.getElementById('graph');

    const graph = new mxGraph(container)
    const parent = graph.getDefaultParent()
    graph.setTooltips(true)

    graph.getModel().beginUpdate()
    try {


      var addCircle = (circles, radius, id, name) => {
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

      const bundleComponentCircles = {};
      const componentMessageCircles = {}

      const padding = 20;

      const bundleCircles = [];
      for (let bundle of map.bundles) {
        const componentCircles = [];
        for (let component of bundle.components) {
          const messageCircles = [];
          for (let message of component.handledMessages) {
            addCircle(messageCircles, 30 + (map.edges[message.id]? (10*map.edges[message.id].k):0), message.id, message.name);
          }
          componentMessageCircles[component.id] = messageCircles;
          addCircle(componentCircles, (this.diameter(messageCircles) / 2) + padding, component.id, component.name);
        }
        bundleComponentCircles[bundle.id] = componentCircles;

        addCircle(bundleCircles, (this.diameter(componentCircles) / 2) + padding, bundle.id, bundle.name);
      }

      const nodeStyle = "shape=ellipse"


      var diameter = this.diameter(bundleCircles);
      const center = (diameter / 2);

      const minX = Math.min.apply(null, bundleCircles.map(c => c.x - c.r));
      const minY = Math.min.apply(null, bundleCircles.map(c => c.y - c.r));
      for (let c of bundleCircles) {
        var x = c.x - c.r - minX;
        var y = c.y - c.r - minY;
        var bp = graph.insertVertex(parent, null, c.n,
          x,
          y,
          c.r * 2,
          c.r * 2, nodeStyle + ";fillColor=transparent");
        bp.setConnectable(false);

        const _minX = Math.min.apply(null, bundleComponentCircles[c.n].map(h => h.x - h.r));
        const _minY = Math.min.apply(null, bundleComponentCircles[c.n].map(h => h.y - h.r));

        var _diameter = c.r * 2;
        var _centralPoint = this.getCenter(bundleComponentCircles[c.n]);
        const _center = (_diameter / 2);

        for (let _c of bundleComponentCircles[c.n]) {
          var _x = _c.x - _c.r - _minX;
          var _y = _c.y - _c.r - _minY;
          var _bp = graph.insertVertex(bp, null, _c.n,
            _x + (_center - (_centralPoint.x - _minX)),
            _y + (_center - (_centralPoint.y - _minY)),
            _c.r * 2,
            _c.r * 2, nodeStyle + ";fillColor=transparent");
          _bp.setConnectable(false);

          const __minX = Math.min.apply(null, componentMessageCircles[_c.n].map(h => h.x - h.r));
          const __minY = Math.min.apply(null, componentMessageCircles[_c.n].map(h => h.y - h.r));

          var __diameter = _c.r * 2;
          var __centralPoint = this.getCenter(componentMessageCircles[_c.n]);
          const __center = (__diameter / 2);

          for (let __c of componentMessageCircles[_c.n]) {
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


      for(let n in map.edges){
        for(let to of map.edges[n].links){
          graph.insertEdge(parent, null, null,  graph.getModel().getCell(n), graph.getModel().getCell(to))
        }
      }

    } finally {
      graph.getModel().endUpdate()
    }


  }

}
