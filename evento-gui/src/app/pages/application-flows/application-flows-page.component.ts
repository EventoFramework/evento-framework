import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {HandlerService} from '../../services/handler.service';
import {BundleColorService} from '../../services/bundle-color.service';
import * as mermaid from 'mermaid';
import {ActivatedRoute} from "@angular/router";
import {componentColor, graphCenterFit, payloadColor, stringToColour} from "../../services/utils";

declare let mxGraph: any;
declare let mxClient: any;
declare let mxRectangle: any;
declare let mxHierarchicalLayout: any;
declare let mxCellRenderer: any;
declare let mxGraphView: any;
declare let mxConnectionConstraint: any;
declare let mxShape: any;
declare let mxPoint: any;
declare let mxUtils: any;
declare let mxCylinder: any;
declare let mxActor: any;
declare let mxConstants: any;
declare let mxPerimeter: any;
declare let mxStyleRegistry: any;
declare let mxEvent: any;


@Component({
  selector: 'app-application-petri-net',
  templateUrl: './application-flows-page.component.html',
  styleUrls: ['./application-flows-page.component.scss'],
})
export class ApplicationFlowsPage implements OnInit {
  performanceAnalysis = false;
  sources = [];
  private network: any;

  @ViewChild('container', {static: true}) container: ElementRef;
  bundleActiveThreads = {};
  maxFlowThroughput = {};
  bundles = [];

  constructor(private handlerService: HandlerService,
              private bundleColorService: BundleColorService,
              private route: ActivatedRoute) {

    // Flexible cylinder3 Shape with offset label
    function CylinderShape3(bounds, fill, stroke, strokewidth) {
      mxShape.call(this);
      this.bounds = bounds;
      this.fill = fill;
      this.stroke = stroke;
      this.strokewidth = (strokewidth != null) ? strokewidth : 1;
    };

    mxUtils.extend(CylinderShape3, mxCylinder);

    CylinderShape3.prototype.size = 15;

    CylinderShape3.prototype.paintVertexShape = function (c, x, y, w, h) {
      var size = Math.max(0, Math.min(h * 0.5, parseFloat(mxUtils.getValue(this.style, 'size', this.size))));
      var lid = mxUtils.getValue(this.style, 'lid', true);

      c.translate(x, y);

      if (size == 0) {
        c.rect(0, 0, w, h);
        c.fillAndStroke();
      } else {
        c.begin();

        if (lid) {
          c.moveTo(0, size);
          c.arcTo(w * 0.5, size, 0, 0, 1, w * 0.5, 0);
          c.arcTo(w * 0.5, size, 0, 0, 1, w, size);
        } else {
          c.moveTo(0, 0);
          c.arcTo(w * 0.5, size, 0, 0, 0, w * 0.5, size);
          c.arcTo(w * 0.5, size, 0, 0, 0, w, 0);
        }

        c.lineTo(w, h - size);
        c.arcTo(w * 0.5, size, 0, 0, 1, w * 0.5, h);
        c.arcTo(w * 0.5, size, 0, 0, 1, 0, h - size);
        c.close();
        c.fillAndStroke();

        c.setShadow(false);

        if (lid) {
          c.begin();
          c.moveTo(w, size);
          c.arcTo(w * 0.5, size, 0, 0, 1, w * 0.5, 2 * size);
          c.arcTo(w * 0.5, size, 0, 0, 1, 0, size);
          c.stroke();
        }
      }
    };

    CylinderShape3.prototype.getLabelMargins = function (rect) {
      if (mxUtils.getValue(this.style, 'boundedLbl', false)) {
        var size = mxUtils.getValue(this.style, 'size', 15);

        if (!mxUtils.getValue(this.style, 'lid', true)) {
          size /= 2;
        }

        return new mxRectangle(0, Math.min(rect.height * this.scale, size * 2 * this.scale), 0, Math.max(0, size * 0.3 * this.scale));
      }

      return null;
    };

    CylinderShape3.prototype.getConstraints = function (style, w, h) {
      var constr = [];
      var s = Math.max(0, Math.min(h, parseFloat(mxUtils.getValue(this.style, 'size', this.size))));

      constr.push(new mxConnectionConstraint(new mxPoint(0.5, 0), false));
      constr.push(new mxConnectionConstraint(new mxPoint(0, 0.5), false));
      constr.push(new mxConnectionConstraint(new mxPoint(0.5, 1), false));
      constr.push(new mxConnectionConstraint(new mxPoint(1, 0.5), false));

      constr.push(new mxConnectionConstraint(new mxPoint(0, 0), false, null, 0, s));
      constr.push(new mxConnectionConstraint(new mxPoint(1, 0), false, null, 0, s));
      constr.push(new mxConnectionConstraint(new mxPoint(1, 1), false, null, 0, -s));
      constr.push(new mxConnectionConstraint(new mxPoint(0, 1), false, null, 0, -s));

      constr.push(new mxConnectionConstraint(new mxPoint(0, 0), false, null, 0, s + (h * 0.5 - s) * 0.5));
      constr.push(new mxConnectionConstraint(new mxPoint(1, 0), false, null, 0, s + (h * 0.5 - s) * 0.5));
      constr.push(new mxConnectionConstraint(new mxPoint(1, 0), false, null, 0, h - s - (h * 0.5 - s) * 0.5));
      constr.push(new mxConnectionConstraint(new mxPoint(0, 0), false, null, 0, h - s - (h * 0.5 - s) * 0.5));

      constr.push(new mxConnectionConstraint(new mxPoint(0.145, 0), false, null, 0, s * 0.29));
      constr.push(new mxConnectionConstraint(new mxPoint(0.855, 0), false, null, 0, s * 0.29));
      constr.push(new mxConnectionConstraint(new mxPoint(0.855, 1), false, null, 0, -s * 0.29));
      constr.push(new mxConnectionConstraint(new mxPoint(0.145, 1), false, null, 0, -s * 0.29));

      return (constr);
    };


    mxCellRenderer.registerShape('cylinder3', CylinderShape3);

    // Step shape
    function StepShape()
    {
      mxActor.call(this);
    };
    mxUtils.extend(StepShape, mxActor);
    StepShape.prototype.size = 0.2;
    StepShape.prototype.fixedSize = 20;
    StepShape.prototype.isRoundable = function()
    {
      return true;
    };
    StepShape.prototype.redrawPath = function(c, x, y, w, h)
    {
      var fixed = mxUtils.getValue(this.style, 'fixedSize', '0') != '0';
      var s = (fixed) ? Math.max(0, Math.min(w, parseFloat(mxUtils.getValue(this.style, 'size', this.fixedSize)))) :
        w * Math.max(0, Math.min(1, parseFloat(mxUtils.getValue(this.style, 'size', this.size))));
      var arcSize = mxUtils.getValue(this.style, mxConstants.STYLE_ARCSIZE, mxConstants.LINE_ARCSIZE) / 2;
      this.addPoints(c, [new mxPoint(0, 0), new mxPoint(w - s, 0), new mxPoint(w, h / 2), new mxPoint(w - s, h),
        new mxPoint(0, h), new mxPoint(s, h / 2)], this.isRounded, arcSize, true);
      c.end();
    };

    mxCellRenderer.registerShape('step', StepShape);

    StepShape.prototype.constraints = [new mxConnectionConstraint(new mxPoint(0.25, 0), true),
      new mxConnectionConstraint(new mxPoint(0.5, 0), true),
      new mxConnectionConstraint(new mxPoint(0.75, 0), true),
      new mxConnectionConstraint(new mxPoint(0.25, 1), true),
      new mxConnectionConstraint(new mxPoint(0.5, 1), true),
      new mxConnectionConstraint(new mxPoint(0.75, 1), true),
      new mxConnectionConstraint(new mxPoint(0, 0.25), true),
      new mxConnectionConstraint(new mxPoint(0, 0.5), true),
      new mxConnectionConstraint(new mxPoint(0, 0.75), true),
      new mxConnectionConstraint(new mxPoint(1, 0.25), true),
      new mxConnectionConstraint(new mxPoint(1, 0.5), true),
      new mxConnectionConstraint(new mxPoint(1, 0.75), true)];

    // Step Perimeter
    mxPerimeter.StepPerimeter = function (bounds, vertex, next, orthogonal)
    {
      var fixed = mxUtils.getValue(vertex.style, 'fixedSize', '0') != '0';
      var size = (fixed) ? StepShape.prototype.fixedSize : StepShape.prototype.size;

      if (vertex != null)
      {
        size = mxUtils.getValue(vertex.style, 'size', size);
      }

      if (fixed)
      {
        size *= vertex.view.scale;
      }

      var x = bounds.x;
      var y = bounds.y;
      var w = bounds.width;
      var h = bounds.height;

      var cx = bounds.getCenterX();
      var cy = bounds.getCenterY();

      var direction = (vertex != null) ? mxUtils.getValue(
        vertex.style, mxConstants.STYLE_DIRECTION,
        mxConstants.DIRECTION_EAST) : mxConstants.DIRECTION_EAST;
      var points;

      if (direction == mxConstants.DIRECTION_EAST)
      {
        var dx = (fixed) ? Math.max(0, Math.min(w, size)) : w * Math.max(0, Math.min(1, size));
        points = [new mxPoint(x, y), new mxPoint(x + w - dx, y), new mxPoint(x + w, cy),
          new mxPoint(x + w - dx, y + h), new mxPoint(x, y + h),
          new mxPoint(x + dx, cy), new mxPoint(x, y)];
      }
      else if (direction == mxConstants.DIRECTION_WEST)
      {
        var dx = (fixed) ? Math.max(0, Math.min(w, size)) : w * Math.max(0, Math.min(1, size));
        points = [new mxPoint(x + dx, y), new mxPoint(x + w, y), new mxPoint(x + w - dx, cy),
          new mxPoint(x + w, y + h), new mxPoint(x + dx, y + h),
          new mxPoint(x, cy), new mxPoint(x + dx, y)];
      }
      else if (direction == mxConstants.DIRECTION_NORTH)
      {
        var dy = (fixed) ? Math.max(0, Math.min(h, size)) : h * Math.max(0, Math.min(1, size));
        points = [new mxPoint(x, y + dy), new mxPoint(cx, y), new mxPoint(x + w, y + dy),
          new mxPoint(x + w, y + h), new mxPoint(cx, y + h - dy),
          new mxPoint(x, y + h), new mxPoint(x, y + dy)];
      }
      else
      {
        var dy = (fixed) ? Math.max(0, Math.min(h, size)) : h * Math.max(0, Math.min(1, size));
        points = [new mxPoint(x, y), new mxPoint(cx, y + dy), new mxPoint(x + w, y),
          new mxPoint(x + w, y + h - dy), new mxPoint(cx, y + h),
          new mxPoint(x, y + h - dy), new mxPoint(x, y)];
      }

      var p1 = new mxPoint(cx, cy);

      if (orthogonal)
      {
        if (next.x < x || next.x > x + w)
        {
          p1.y = next.y;
        }
        else
        {
          p1.x = next.x;
        }
      }

      return mxUtils.getPerimeterPoint(points, p1, next);
    };

    mxStyleRegistry.putValue('stepPerimeter', mxPerimeter.StepPerimeter);
  }


  async ngOnInit() {

    const handlerId = this.route.snapshot.params.handlerId;
    const container = this.container.nativeElement;
    this.network = handlerId === 'all' ? await this.handlerService.getQueueNet() : await this.handlerService.getQueueNet(handlerId);

    this.sources = [];


    const tMap = {}
    for (const node of this.network.nodes) {
      if (node.type === 'Source') {
        node.throughtput = 0.01;
        node.meanServiceTime = 0.01;
        this.sources.push(node);
      }
      if (!node.meanServiceTime) {
        node.meanServiceTime = 0.0;
      }
      if (node.numServers) {
        if (!tMap[node.bundle + node.component + node.numServers]) {
          tMap[node.bundle + node.component + node.numServers] = [];
        }
        tMap[node.bundle + node.component + node.numServers].push(node);
      }
    }

    for (const block in tMap) {
      for (const node of tMap[block]) {
        node.numServers = node.numServers / tMap[block].length;
      }
    }


    this.drawGraph(container);
  }


  togglePerformanceAnalysis(event: any) {
    this.performanceAnalysis = event.detail.checked;
    return this.drawGraph(this.container.nativeElement);
  }

  private drawGraph(container) {
    setTimeout(() => {
      container.innerHTML = '';
      const graph = new mxGraph(container);
      const parent = graph.getDefaultParent();
      graph.setTooltips(true);

      // Enables panning with left mouse button
      graph.panningHandler.useLeftButtonForPanning = true;
      graph.panningHandler.ignoreCell = true;
      graph.container.style.cursor = 'move';
      graph.setPanning(true);
      graph.resizeContainer = false;
      graph.htmlLabels = true;


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



      const edges = [];
      const layout = new mxHierarchicalLayout(graph, 'west');
      layout.traverseAncestors = false;
      graph.getModel().beginUpdate();
      try {

        const nodesRef = {};
        for (const node of this.network.nodes) {
          nodesRef[node.id] = node;
          if (node.type != 'Source') {
            node.throughtput = 0;
          }
          node.flowThroughtput = 0;
        }


        if (this.performanceAnalysis) {
          var q = [];
          for (const s of this.sources) {
            s.meanServiceTime = 1 / s.throughtput;
            s.flowThroughtput = s.throughtput;
            s.flow = s.id;
            q.push(s);
          }
          while (q.length > 0) {
            const n = q.shift();
            for (const t of n.target) {
              var target = nodesRef[t];
              target.throughtput += n.throughtput;
              if (target.numServers) {
                const t = target.numServers / target.meanServiceTime;
                if (t < target.throughtput) {
                  target.throughtput = t;
                }
              }
              if (!target.flowThroughtput || (target.flowThroughtput > n.flowThroughtput))
                target.flowThroughtput = n.flowThroughtput;
              target.flow = n.flow;
              q.push(target);
            }
          }

          this.bundleActiveThreads = {};
          this.maxFlowThroughput = {};

          for (const node of this.network.nodes) {
            const nc = node.throughtput * node.meanServiceTime;
            node.customers = (node.numServers ? Math.max(node.numServers, nc) : nc);
            if (node.bundle) {
              if (!this.bundleActiveThreads[node.bundle]) {
                this.bundleActiveThreads[node.bundle] = 0;
              }
              this.bundleActiveThreads[node.bundle] += node.customers;
              if (!this.bundles.includes(node.bundle)) {
                this.bundles.push(node.bundle);
              }
            }
            node.isBottleneck = false;
            if (!this.maxFlowThroughput[node.flow]) {
              this.maxFlowThroughput[node.flow] = node;
              node.isBottleneck = true;
            } else if (nodesRef[node.flow].throughtput > node.throughtput && node.type !== 'Sink') {
              //this.maxFlowThroughput[node.flow].isBottleneck = false;
              if (this.maxFlowThroughput[node.flow].throughtput > node.throughtput) {
                this.maxFlowThroughput[node.flow] = node;
              }
              node.isBottleneck = true;
            }
          }

        }


        const vertexRef = {}

        const serviceStationStyle = 'shape=rectangle;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;verticalAlign=top;horizontal=1;labelPosition=center;verticalLabelPosition=middle;align=center;';
        const sinkStyle = 'shape=ellipse;whiteSpace=wrap;perimeter=ellipsePerimeter;strokeColor=grey;fontColor=black;fillColor=transparent';
        const performanceBoxStyle = '';
        const edgeStyle = 'edgeStyle=elbowEdgeStyle;rounded=1;jumpStyle=arc;orthogonalLoop=1;jettySize=auto;html=1;endArrow=block;endFill=1;orthogonal=1;strokeWidth=1;';
        for (const node of this.network.nodes) {
          console.log(node);
          if (node.component === 'Gateway') {

            vertexRef[node.id] = graph.insertVertex(parent, node.id, `<span class="title" style="color: ${payloadColor[node.actionType]} !important;">${node.action}</span>`, null, null, text.length * 10,
              60,
              'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor='+(node.actionType ? payloadColor[node.actionType] : 'black')+';fontColor=#333333;strokeWidth=3;');


          } else if (node.type === 'Source') {
            var text = node.name;
            vertexRef[node.id] = graph.insertVertex(parent, node.id, `<span class="title" style="color: ${payloadColor[node.actionType]} !important;">${node.action}</span>`, null, null, text.length * 10,
              60,
              'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor='+(node.actionType ? payloadColor[node.actionType] : 'black')+';fontColor=#333333;strokeWidth=3;');
          } else if (node.bundle === 'event-store') {
            vertexRef[node.id] = graph.insertVertex(parent, node.id, 'SSS', null, null, 60,
              80,
              'shape=cylinder3;whiteSpace=wrap;html=1;boundedLbl=1;backgroundOutline=1;size=15;rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=#000000;fontColor=#333333;strokeWidth=3;');
          } else if (node.type === 'Sink') {
            node.name = node.type;
            vertexRef[node.id] = graph.insertVertex(parent, node.id, "Sink", null, null, 50,
              50,
              sinkStyle);
          } else {
            vertexRef[node.id] = graph.insertVertex(parent, node.id,
              `<b style="color: ${stringToColour(node.bundle)}">${node.bundle}</b>
                <span class="title" style="color: ${componentColor[node.componentType]} !important">${node.component}</span>
              `
              , null, null, Math.max(node.component.length, node.bundle.length) * 10 + 25,
              80,
              'rounded=1;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=' + stringToColour(node.bundle) + ';fontColor=#333333;strokeWidth=3;');
          }
          /*
          if (node.type === 'Sink') {
            node.name = node.type;
            vertexRef[node.id] = graph.insertVertex(parent, node.id, "Sink", null, null, 50,
              50,
              sinkStyle);
          } else if (node.type === 'Source') {
            var text = '\n' + node.name;
            vertexRef[node.id] = graph.insertVertex(parent, node.id, text, null, null, text.length * 10,
              40,
              serviceStationStyle);
          } else {
            node.name = node.bundle + '\n\n' + node.component + '\n\n' + node.action;
            node.name = node.action;
            let additionalStyles = 'fillColor=' + this.bundleColorService.getColorForBundle(node.bundle) + ';';
            if (node.isBottleneck) {
              additionalStyles += 'strokeColor=#ff0000;strokeWidth=3;'
            }
            const width = Math.max(node.bundle.length, node.component.length, node.action.length, this.performanceAnalysis ? 25 : 0) * 10;
            let height = 90;
            let text = node.name;

            if (node.bundle === 'event-store' || node.component === 'SagaStore' || node.component === 'ProjectorStore') {
              additionalStyles += 'shape=cylinder;verticalAlign=bottom;spacingBottom=' + (this.performanceAnalysis ? 100 : 20) + ';';
              height += 70;
            }
            if (node.component === 'Gateway') {
              additionalStyles += 'shape=cylinder;rotation=90;horizontal=0;spacingBottom=' + (this.performanceAnalysis ? 100 : 20) + ';';
              height += 70;
            }
            const bHeight = height;
            if (this.performanceAnalysis && node.meanServiceTime) {
              height += 90;
            }
            vertexRef[node.id] = graph.insertVertex(parent, node.id, '\n' + text, null, null, width,
              height,
              serviceStationStyle + additionalStyles);
            if (this.performanceAnalysis && node.meanServiceTime) {
              let txt = 'Service time: ' + (node.meanServiceTime.toFixed(4)) + ' [ms]';
              const w = 210;
              graph.insertVertex(vertexRef[node.id], node.id + '_st', txt, (width / 2) - (w / 2), bHeight + 10, w,
                30,
                performanceBoxStyle);


              txt = 'Customers: ' + node.customers.toFixed(4) + (node.numServers ? ('/' + node.numServers.toFixed(4)) : '') + ' [r]';
              graph.insertVertex(vertexRef[node.id], node.id + '_cn', txt, (width / 2) - (w / 2), bHeight + 30 + 15, w,
                30,
                performanceBoxStyle);
            }
          }*/
        }


        for (const node of this.network.nodes) {
          const targets = [];
          for (const t of node.target) {
            targets.push(nodesRef[t]);
          }
          for (const target of targets.sort((a, b) => a?.async - b?.async)) {
            if (this.performanceAnalysis) {
              const source = nodesRef[node.id];
              const ql = (source.throughtput - target.throughtput) * target.meanServiceTime;
              const ratio = source.throughtput / source.flowThroughtput;
              const c = this.perc2color(ratio * 100);
              var txt = node.throughtput.toFixed(4) + "  [r/ms]";
              txt += "\n" + ql.toFixed(4) + " [ql/ms]";
              graph.insertEdge(parent, null, txt, vertexRef[node.id],
                vertexRef[target.id], edgeStyle + ';' + (target.async ? 'dashed=1' : 'dashed=0') + ';strokeWidth=' + (ratio * 20) + ';strokeColor=' + c);
            } else {
              edges.push(graph.insertEdge(parent, null, "", vertexRef[node.id],
                vertexRef[target.id], edgeStyle + ';' + (target.async ? 'dashed=1' : 'dashed=0') +';' + (target.async ? 'strokeColor=#999999' : 'strokeColor=#000')));
            }
          }
        }


        // Executes the layout
        layout.execute(parent);
      } finally {
        graph.getModel().endUpdate();
      }



      graphCenterFit(graph, container);

      for (const e of edges) {
        var state = graph.view.getState(e);
        state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
      }

      graph.view.addListener(mxEvent.AFTER_RENDER, function () {
        for (const e of edges) {
          var state = graph.view.getState(e);
          state.shape.node.getElementsByTagName('path')[1].setAttribute('class', 'flow');
        }
      });
    }, 500);



  }

  perc2color(perc) {
    let r, g, b = 0;
    if (perc < 50) {
      r = 255;
      g = Math.round(5.1 * perc);
    } else {
      g = 255;
      r = Math.round(510 - 5.10 * perc);
    }
    const h = r * 0x10000 + g * 0x100 + b * 0x1;
    return '#' + ('000000' + h.toString(16)).slice(-6);
  }

  runAnalysis() {
    return this.drawGraph(this.container.nativeElement);
  }
}
