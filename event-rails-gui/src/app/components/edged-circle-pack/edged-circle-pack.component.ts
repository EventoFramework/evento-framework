import {Component, OnInit} from '@angular/core';
import {ApplicationMap} from "./model";

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

  ngOnInit() {

    const map: ApplicationMap = {
      nodes: [
        {
          id: "bundle1",
          name: "bundle1",
          children: [{
            id: "component1",
            name: "component1",
            children: [
              {
                id: "node1",
                name: "node1",
                children: []
              },
              {
                id: "node2",
                name: "node2",
                children: []
              }
            ]
          }]
        },
        {
          id: "bundle2",
          name: "bundle2",
          children: [{
            id: "component2",
            name: "component2",
            children: [
              {
                id: "node3",
                name: "node3",
                children: []
              },
              {
                id: "node4",
                name: "node4",
                children: []
              },
              {
                id: "node5",
                name: "node5",
                children: []
              }
            ]
          }]
        },
        {
          id: "bundle3",
          name: "bundle3",
          children: [{
            id: "component3",
            name: "component3",
            children: [
              {
                id: "node6",
                name: "node6",
                children: []
              }]
          },
            {
              id: "component4",
              name: "component4",
              children: [
                {
                  id: "node7",
                  name: "node7",
                  children: []
                },
                {
                  id: "node7",
                  name: "node7",
                  children: []
                }]
            }]
        }
      ],
      edges: []
    }

    const container = <HTMLElement>document.getElementById('graph');

    const graph = new mxGraph(container)
    const parent = graph.getDefaultParent()
    graph.setTooltips(true)

    graph.getModel().beginUpdate()
    try {

      var nodeStyle = "shape=ellipse;"

      const r = 15;
      const d = r * 2;

      var n1 = {x: 0, y: 0, r: 15, n: "n1"}
      var n2 = {x: n1.r * 2, y: 0, r: 15, n: "n2"}

      var circles = [n1, n2];

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

      const third_vertex = (x2, y2, x3, y3, d1, d3) => {
        var d2 = ((x3 - x2) ** 2 + (y3 - y2) ** 2) ** 0.5;
        var k = (d2 ** 2 + d1 ** 2 - d3 ** 2) / (2 * d2);
        var h = (d1 ** 2 - k ** 2) ** 0.5;

        return [x2 + (k / d2) * (x3 - x2) - (h / d2) * (y3 - y2),
          y2 + (k / d2) * (y3 - y2) + (h / d2) * (x3 - x2)];

        //vertex_1b(1) = x2 + (k/d2)*(x3 - x2) + (h/d2)*(y3 - y2);
        //vertex_1b(2) = y2 + (k/d2)*(y3 - y2) - (h/d2)*(x3 - x2);
      }


      var g_cx;
      var g_cy;
      var g_cx;



      var addCircle = (radius, name) => {

        const hull = makeHull(circles);


        var cx = 0;
        var cy = 0;
        var max_d = 0;

        for (let h of hull) {

          cx += h.x;
          cy += h.y;
          for (let h1 of hull) {

            var d = ((((h.x - h1.x) ** 2 + (h.y - h1.y) ** 2) ** 0.5));
            d = d + h.r + h1.r
            if (max_d == null || d > max_d) {
              max_d = d;
              console.log(h, h1)
            }
          }
        }
        cx = cx / hull.length;
        cy = cy / hull.length;

        var min_d = null;

        var c1 = hull[0];
        var c2 = hull[1];

        for (let i = 0; i < hull.length; i++) {
          var h = hull[i];
          var d = ((h.x - cx) ** 2 + (h.y - cy) ** 2) ** 0.5;
          if (min_d == null || d < min_d) {
            min_d = d;
            c1 = hull[i];
            c2 = hull[(i + 1) % hull.length];
          }
        }


        console.log("c1", c1);
        console.log("c2", c2);


        // use cosine law to calculate COS for c1
        var p = third_vertex(c1.x, c1.y, c2.x, c2.y,
          c1.r + radius,
          c2.r + radius
        )

        var c3 = {
          x: p[0],
          y: p[1],
          r: radius,
          n: name
        }
        console.log("c3", c3)

        circles.push(c3)

      }

      addCircle(20, "n3")
      addCircle(25, "n4")
      addCircle(30, "n5")
      addCircle(25, "n6")
      addCircle(20, "n7")
      addCircle(25, "n8")
      addCircle(20, "n3")
      addCircle(25, "n4")
      addCircle(20, "n3")
      addCircle(20, "n7")
      addCircle(25, "n8")
      addCircle(20, "n3")



      var m = graph.insertVertex(parent, null, null, 0, 0, max_d, max_d, nodeStyle);

      const center = (max_d / 2) - (cx/4);
      for (let c of circles) {
        graph.insertVertex(m, null, c.n, center + c.x - c.r, center + c.y - c.r, c.r * 2, c.r * 2, nodeStyle);
      }

    } finally {
      graph.getModel().endUpdate()
    }


  }

}
