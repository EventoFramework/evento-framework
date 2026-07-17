import cytoscape from 'cytoscape';
// Dagre runs the layered layout in-thread (pure JS, no Web Worker). We use it
// instead of cytoscape-elk: elkjs's bundled build runs inside a Web Worker that
// fails silently in this webpack bundle (the layout promise rejects with no
// catch, positions are never applied, and every node stays stacked at (0,0)).
// cytoscape-dagre is a CommonJS module — resolve the callable defensively so
// bundler interop can't hand cytoscape.use() the wrong value.
// @ts-ignore - no bundled types for the dagre extension
import dagreDefault from 'cytoscape-dagre';
// @ts-ignore
import * as dagreNamespace from 'cytoscape-dagre';

let dagreRegistered = false;
let dagreAvailable = false;
function ensureDagre(): void {
  if (dagreRegistered) return;
  dagreRegistered = true;
  const candidates: any[] = [dagreDefault, (dagreNamespace as any)?.default, dagreNamespace];
  const register = candidates.find((c) => typeof c === 'function');
  if (register) {
    try {
      cytoscape.use(register);
      dagreAvailable = true;
    } catch {
      dagreAvailable = false;
    }
  }
}

/** Read a CSS custom property off :root (canvas can't resolve var(), so we
 *  snapshot the token values at render time and re-read them on theme change). */
export function cssToken(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

export interface EventoNode {
  /** Unique node id (caller-controlled — reuse the same id to merge boxes). */
  id: string;
  label: string;
  /** Semantic border color (component/payload color). */
  color: string;
  /** Emphasized node (thicker border, bold, colored label). */
  primary?: boolean;
  /** Route to navigate to on double-click, e.g. `/payload-info/Foo`. */
  route?: string;
  /** Source location for the "Open Repository" context menu. */
  repo?: {bundleId: string; path: string; line: number};
  /**
   * Shape override (default round-rectangle). The special value `cylinder`
   * renders the classic database symbol (event-store) via an inline SVG —
   * Cytoscape's built-in `barrel` bulges sideways and reads wrong.
   */
  shape?: string;
}

/** Inline-SVG database cylinder used for event-store nodes. The explicit
 *  width/height set the raster resolution: without them the browser rasterizes
 *  at the tiny viewBox size and the cylinder blurs when zoomed in. */
function cylinderSvg(stroke: string, fill: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 130" width="600" height="780">` +
    `<path d="M4 18 a46 14 0 0 1 92 0 v94 a46 14 0 0 1 -92 0 z" fill="${fill}" stroke="${stroke}" stroke-width="3"/>` +
    `<ellipse cx="50" cy="18" rx="46" ry="14" fill="${fill}" stroke="${stroke}" stroke-width="3"/>` +
    `</svg>`;
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
}

export interface EventoEdge {
  source: string;
  target: string;
  /** Dashed + animated "flow" edge (default true). */
  flow?: boolean;
  /** Static dashed line (async links) without the marching-ants animation. */
  dashed?: boolean;
  /** Edge label (supports \n for multi-line, e.g. throughput read-outs). */
  label?: string;
  /** Stroke width override (perf overlay encodes utilization). */
  width?: number;
  /** Stroke color override (perf overlay heat color); defaults to theme. */
  color?: string;
  opacity?: number;
}

export interface EventoGraphOptions {
  direction?: 'RIGHT' | 'DOWN';
  onNavigate?: (route: string) => void;
  /** Build a repository URL from a node's `repo`, or null if unavailable. */
  repoLink?: (repo: EventoNode['repo']) => string | null;
  /**
   * Full control over the right-click menu: return the items for a node (or
   * empty/none to show nothing). Takes precedence over `repoLink`.
   */
  contextMenu?: (nodeId: string, node: EventoNode) => Array<{label: string; action: () => void}>;
}

export interface EventoGraphHandle {
  cy: cytoscape.Core;
  destroy: () => void;
}

/**
 * Renders an Evento node/edge graph into `container` using Cytoscape + the ELK
 * layered layout. Replaces the legacy mxGraph renderers: read-only, wheel-zoom,
 * drag-pan, fit-to-view, double-click drill-down, right-click repo link, and
 * animated dashed "flow" edges — all theme-aware.
 */
export function createEventoGraph(
  container: HTMLElement,
  nodes: EventoNode[],
  edges: EventoEdge[],
  opts: EventoGraphOptions = {},
): EventoGraphHandle {
  ensureDagre();

  const palette = () => ({
    surface: cssToken('--evento-surface', '#ffffff'),
    text: cssToken('--evento-text', '#14201f'),
    edge: cssToken('--evento-text-faint', '#8a9997'),
  });
  let p = palette();

  const nodeById: Record<string, EventoNode> = {};
  const elements: cytoscape.ElementDefinition[] = [
    ...nodes.map((n) => {
      nodeById[n.id] = n;
      // Explicit dimensions so the layout has real sizes at layout time
      // (label-based sizing isn't measured yet when it runs synchronously).
      // Multi-line labels get taller boxes sized by their longest line.
      const lines = (n.label || '').split('\n');
      const longest = lines.reduce((m, l) => Math.max(m, l.length), 3);
      const isCylinder = n.shape === 'cylinder';
      return {
        classes: isCylinder ? 'cylinder' : '',
        data: {
          id: n.id,
          label: n.label,
          color: n.color,
          route: n.route,
          repo: n.repo,
          bg: p.surface,
          fg: n.primary ? n.color : p.text,
          bw: n.primary ? 4 : 2,
          fw: n.primary ? 700 : 500,
          shape: isCylinder ? 'rectangle' : (n.shape || 'round-rectangle'),
          bgImg: isCylinder ? cylinderSvg(n.color, p.surface) : '',
          w: Math.max(80, longest * 8 + 20),
          h: 30 + lines.length * 16 + (isCylinder ? 26 : 0),
        },
      };
    }),
    ...edges.map((e, i) => ({
      data: {
        id: `e${i}`,
        source: e.source,
        target: e.target,
        lbl: e.label || '',
        ew: e.width ?? 1.5,
        lc: e.color || p.edge,
        // Custom-colored edges keep their color on theme flips.
        cc: e.color ? 1 : 0,
        eo: e.opacity ?? 1,
        // Placeholders until computeElbows() derives the real geometry from
        // the laid-out node positions.
        sw: '0.5 0.5',
        sd: '0 0',
        sep: opts.direction === 'DOWN' ? '180deg' : '90deg',
        tep: opts.direction === 'DOWN' ? '0deg' : '270deg',
      },
      classes: [e.flow === false ? '' : 'flow', e.dashed ? 'dashed' : ''].join(' ').trim(),
    })),
  ];

  const cy = cytoscape({
    container,
    elements,
    minZoom: 0.1,
    maxZoom: 4,
    wheelSensitivity: 0.3,
    // Read-only feel: users can't drag nodes. Do NOT use `autolock` here —
    // locked nodes are excluded from layout positioning, which left every node
    // stacked at (0,0) regardless of layout engine.
    autoungrabify: true,
    boxSelectionEnabled: false,
    style: [
      {
        selector: 'node',
        style: {
          shape: 'data(shape)' as any,
          'corner-radius': '10' as any,
          'background-color': 'data(bg)',
          'border-color': 'data(color)',
          'border-width': 'data(bw)',
          label: 'data(label)',
          color: 'data(fg)',
          'font-size': 13,
          'font-weight': 'data(fw)' as any,
          'text-valign': 'center',
          'text-halign': 'center',
          'text-wrap': 'wrap',
          'text-max-width': 'data(w)',
          width: 'data(w)',
          height: 'data(h)',
        },
      },
      {
        // Database-cylinder nodes: the SVG *is* the visual; hide the box.
        selector: 'node.cylinder',
        style: {
          'background-opacity': 0,
          'border-width': 0,
          'background-image': 'data(bgImg)' as any,
          'background-width': '100%' as any,
          'background-height': '100%' as any,
          // Nudge the label below the cylinder lid.
          'text-margin-y': 8 as any,
        },
      },
      {
        selector: 'edge',
        style: {
          width: 'data(ew)' as any,
          'line-color': 'data(lc)' as any,
          'target-arrow-color': 'data(lc)' as any,
          opacity: 'data(eo)' as any,
          'target-arrow-shape': 'triangle',
          'arrow-scale': 0.9,
          label: 'data(lbl)' as any,
          'font-size': 9,
          color: p.text,
          'text-wrap': 'wrap',
          'text-background-color': p.surface,
          'text-background-opacity': 0.85,
          'text-background-padding': '2px',
          // Legacy mxGraph-style routing: a short fixed jetty leaves the
          // source's flow side, a straight diagonal crosses to the target,
          // and a jetty enters the target's opposite side (two joints).
          // Ports are distributed along the node side per edge — geometry is
          // computed post-layout by computeElbows().
          'curve-style': 'segments',
          // Measure segment weights/distances against the manual ports (not
          // the default node-center intersection line) so computeElbows()'s
          // math matches the renderer exactly.
          'edge-distances': 'endpoints' as any,
          'segment-weights': 'data(sw)' as any,
          'segment-distances': 'data(sd)' as any,
          'source-endpoint': 'data(sep)' as any,
          'target-endpoint': 'data(tep)' as any,
          'segment-radii': 8 as any,
          'radius-type': 'arc-radius' as any,
        },
      },
      {
        selector: 'edge.flow',
        style: {
          'line-style': 'dashed',
          'line-dash-pattern': [6, 4],
        },
      },
      {
        // Static dashed (async) — no marching-ants animation.
        selector: 'edge.dashed',
        style: {
          'line-style': 'dashed',
          'line-dash-pattern': [6, 4],
        },
      },
    ],
  });

  // Run the ELK layered layout explicitly and fit once it settles. Running it
  // after construction (rather than via the constructor option) guarantees the
  // nodes exist with their explicit sizes before layout, and lets us re-fit on
  // completion so the graph is centered instead of stuck in a corner.
  // Recreate mxGraph's elbow geometry from the laid-out positions:
  //  - each edge gets its own exit port, spread along the source's flow side
  //    (and entry port on the target's opposite side), sorted by where the
  //    other endpoint sits so edges never cross at the port;
  //  - a fixed-length jetty (stub) extends from each port, giving the two
  //    joints, with a straight diagonal between the jetty ends.
  // Implemented with `segments`: the two bends are converted into the
  // (weight, perpendicular-distance) form Cytoscape expects.
  const JETTY = 24;
  const horizontal = opts.direction !== 'DOWN';
  const computeElbows = () => {
    const outBy: Record<string, cytoscape.EdgeSingular[]> = {};
    const inBy: Record<string, cytoscape.EdgeSingular[]> = {};
    cy.edges().forEach((e) => {
      (outBy[e.data('source')] = outBy[e.data('source')] || []).push(e);
      (inBy[e.data('target')] = inBy[e.data('target')] || []).push(e);
    });
    const spreadOffset = (list: cytoscape.EdgeSingular[], edge: cytoscape.EdgeSingular,
                          node: cytoscape.NodeSingular, byTarget: boolean) => {
      const cross = (n: cytoscape.NodeSingular) => (horizontal ? n.position('y') : n.position('x'));
      const sorted = list.slice().sort((a, b) =>
        cross(byTarget ? a.target() : a.source()) - cross(byTarget ? b.target() : b.source()));
      const i = sorted.indexOf(edge);
      const n = sorted.length;
      const span = (horizontal ? node.height() : node.width()) * 0.7;
      return n > 1 ? -span / 2 + (span * i) / (n - 1) : 0;
    };
    cy.edges().forEach((e) => {
      const s = e.source();
      const t = e.target();
      const offS = spreadOffset(outBy[s.id()], e, s, true);
      const offT = spreadOffset(inBy[t.id()], e, t, false);
      const p1 = horizontal
        ? {x: s.position('x') + s.width() / 2, y: s.position('y') + offS}
        : {x: s.position('x') + offS, y: s.position('y') + s.height() / 2};
      const p2 = horizontal
        ? {x: t.position('x') - t.width() / 2, y: t.position('y') + offT}
        : {x: t.position('x') + offT, y: t.position('y') - t.height() / 2};
      const gap = horizontal ? Math.abs(p2.x - p1.x) : Math.abs(p2.y - p1.y);
      const j = Math.min(JETTY, gap / 3);
      const b1 = horizontal ? {x: p1.x + j, y: p1.y} : {x: p1.x, y: p1.y + j};
      const b2 = horizontal ? {x: p2.x - j, y: p2.y} : {x: p2.x, y: p2.y - j};
      const v = {x: p2.x - p1.x, y: p2.y - p1.y};
      const len = Math.hypot(v.x, v.y) || 1;
      const nrm = {x: -v.y / len, y: v.x / len};
      const proj = (b: {x: number; y: number}) => {
        const rx = b.x - p1.x;
        const ry = b.y - p1.y;
        return {
          w: (rx * v.x + ry * v.y) / (len * len),
          d: rx * nrm.x + ry * nrm.y,
        };
      };
      const q1 = proj(b1);
      const q2 = proj(b2);
      e.data('sep', horizontal ? `${s.width() / 2}px ${offS}px` : `${offS}px ${s.height() / 2}px`);
      e.data('tep', horizontal ? `${-t.width() / 2}px ${offT}px` : `${offT}px ${-t.height() / 2}px`);
      e.data('sw', `${q1.w.toFixed(4)} ${q2.w.toFixed(4)}`);
      e.data('sd', `${q1.d.toFixed(2)} ${q2.d.toFixed(2)}`);
    });
  };

  const applyFit = () => {
    // Sync the canvas with the container's current size first — fitting against
    // a stale viewport can leave part of the graph outside the visible area.
    cy.resize();
    cy.fit(undefined, 30);
    if (cy.zoom() > 1.5) {
      cy.zoom(1.5);
      cy.center();
    }
  };

  // Keep the whole graph visible when the container changes size (responsive
  // layout, sidebar toggling, first proper measure after render).
  const resizeObserver = new ResizeObserver(() => applyFit());
  resizeObserver.observe(container);

  const dagreOptions: any = {
    name: 'dagre',
    fit: false,
    rankDir: opts.direction === 'DOWN' ? 'TB' : 'LR',
    nodeSep: 18,
    edgeSep: 10,
    rankSep: 70,
  };
  // Built-in fallback — hierarchical and, crucially, never stacked — if the
  // dagre extension somehow failed to register.
  const fallbackOptions: any = {
    name: 'breadthfirst',
    fit: false,
    directed: true,
    spacingFactor: 1.2,
  };

  // Lay out each connected component INDEPENDENTLY and stack them (what the
  // legacy mxGraph hierarchical layout did). A single whole-graph dagre run
  // balances unrelated clusters against each other, which spreads same-column
  // siblings far apart; per-component runs keep each column tightly packed.
  const runLayouts = (options: any) => {
    const comps = cy.elements().components();
    const GAP = 60;
    let offset = 0;
    const runNext = (idx: number) => {
      if (idx >= comps.length) {
        computeElbows();
        applyFit();
        return;
      }
      const comp = comps[idx];
      const layout = comp.layout(options);
      layout.one('layoutstop', () => {
        const bb = comp.boundingBox({});
        comp.nodes().positions((n: any) => {
          const p = n.position();
          return horizontal
            ? {x: p.x - bb.x1, y: p.y - bb.y1 + offset}
            : {x: p.x - bb.x1 + offset, y: p.y - bb.y1};
        });
        offset += (horizontal ? bb.h : bb.w) + GAP;
        runNext(idx + 1);
      });
      layout.run();
    };
    runNext(0);
  };
  try {
    runLayouts(dagreAvailable ? dagreOptions : fallbackOptions);
  } catch {
    runLayouts(fallbackOptions);
  }

  // Double-click / double-tap → drill-down navigation.
  cy.on('dbltap', 'node', (evt) => {
    const route = evt.target.data('route');
    if (route) opts.onNavigate?.(route);
  });

  // Right-click menu: caller-provided items (perf editors, …) or the default
  // "Open Repository" link when the node has a resolvable source location.
  const menu = buildContextMenu();
  cy.on('cxttap', 'node', (evt) => {
    menu.hide();
    const id = evt.target.id();
    let items: Array<{label: string; action: () => void}> = [];
    if (opts.contextMenu) {
      items = opts.contextMenu(id, nodeById[id]) || [];
    } else {
      const repo = evt.target.data('repo');
      const url = repo && opts.repoLink ? opts.repoLink(repo) : null;
      if (url) {
        items = [{label: `Open Repository (${repo.line})`, action: () => window.open(url, '_blank')}];
      }
    }
    if (items.length) {
      menu.show(evt.originalEvent as MouseEvent, items);
    }
  });
  cy.on('tap pan zoom', () => menu.hide());

  // Marching-ants animation on flow edges.
  let raf = 0;
  let offset = 0;
  const flowEdges = cy.edges('.flow');
  const animate = () => {
    offset = (offset - 0.6) % 10000;
    flowEdges.style('line-dash-offset', offset);
    raf = requestAnimationFrame(animate);
  };
  if (flowEdges.nonempty()) raf = requestAnimationFrame(animate);

  // Re-color on theme (light/dark) toggle.
  const observer = new MutationObserver(() => {
    p = palette();
    cy.batch(() => {
      cy.nodes().forEach((n) => {
        n.data('bg', p.surface);
        if (n.hasClass('cylinder')) {
          n.data('bgImg', cylinderSvg(n.data('color'), p.surface));
        }
        if (!n.data('route') || n.data('fw') !== 700) {
          n.data('fg', n.data('fw') === 700 ? n.data('color') : p.text);
        }
      });
      // Edge colors are data-driven; only re-theme edges without a custom
      // (perf-overlay) color.
      cy.edges().forEach((e) => {
        if (!e.data('cc')) {
          e.data('lc', p.edge);
        }
      });
      cy.style()
        .selector('edge')
        .style({color: p.text, 'text-background-color': p.surface} as any)
        .update();
    });
  });
  observer.observe(document.documentElement, {attributes: true, attributeFilter: ['class']});

  return {
    cy,
    destroy: () => {
      if (raf) cancelAnimationFrame(raf);
      observer.disconnect();
      resizeObserver.disconnect();
      menu.destroy();
      cy.destroy();
    },
  };
}

/** Minimal floating context menu (one or more actions). */
function buildContextMenu() {
  const el = document.createElement('div');
  el.className = 'evento-graph-menu';
  el.style.display = 'none';
  document.body.appendChild(el);

  function show(evt: MouseEvent, items: Array<{label: string; action: () => void}>) {
    el.innerHTML = '';
    for (const item of items) {
      const row = document.createElement('div');
      row.className = 'evento-graph-menu-item';
      row.textContent = item.label;
      row.addEventListener('click', () => {
        item.action();
        hide();
      });
      el.appendChild(row);
    }
    el.style.left = `${evt.clientX}px`;
    el.style.top = `${evt.clientY}px`;
    el.style.display = 'block';
    evt.preventDefault();
  }
  function hide() {
    el.style.display = 'none';
  }
  function destroy() {
    el.remove();
  }
  return {show, hide, destroy};
}
