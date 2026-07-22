import {AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild} from '@angular/core';
import {HandlerService} from '../../services/handler.service';
import {LoadingController, NavController} from '@ionic/angular';
import {componentColor, getColorForBundle, payloadColor} from '../../services/utils';
import cytoscape from 'cytoscape';
import {hierarchy, pack} from 'd3-hierarchy';

/** Read a CSS custom property off :root (canvas can't resolve var()). */
function cssToken(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

/**
 * Whole-application view: bundles > components > handlers as organically
 * packed circles (d3-hierarchy `pack`, which nests circles tightly — replacing
 * the old bespoke tangent-circle packing that left most of each parent circle
 * empty). Rendered with Cytoscape at preset positions: invocation (solid) and
 * response (dashed) edges between handler bubbles, bubble zoom on hover with
 * downstream highlight, double-click drill-down into the flows view.
 */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'evento-application-graph',
  templateUrl: './application-graph-diagram.component.html',
  styleUrls: ['./application-graph-diagram.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager,
  standalone: false,
})
export class ApplicationGraphDiagramComponent implements AfterViewInit, OnDestroy {

  @ViewChild('container', {static: true}) container: ElementRef;

  // Handler search: type-ahead over payload/component names; selecting a
  // result pans to the handler and pins the same highlight hover applies.
  searchTerm = '';
  searchResults: Array<{id: string; label: string; component: string; bundle: string}> = [];
  private searchIndex: Array<{id: string; label: string; component: string; bundle: string; norm: string}> = [];
  private pinnedId: string | null = null;

  private cy: cytoscape.Core | null = null;
  private themeObserver: MutationObserver | null = null;
  private resizeObserver: ResizeObserver | null = null;

  constructor(private handlerService: HandlerService,
              private navController: NavController,
              private loadingController: LoadingController) {
  }

  async ngAfterViewInit() {
    const fetchLoading = await this.loadingController.create({message: 'Fetching handlers...'});
    await fetchLoading.present();
    const handlers = await this.handlerService.findAll();
    await fetchLoading.dismiss();

    // ---- Group handlers into bundle > component > handler and resolve the
    // invocation / response-handled-by targets (same model as the old view).
    const priority = ['Aggregate', 'Service', 'Projector', 'Projection', 'Saga', 'Invoker'];
    handlers.sort((a, b) => priority.indexOf(a.componentType) - priority.indexOf(b.componentType));

    const bundles = {};
    for (const handler of handlers) {
      if (!bundles[handler.bundleId]) {
        bundles[handler.bundleId] = {components: {}};
      }
      if (!bundles[handler.bundleId].components[handler.componentName]) {
        bundles[handler.bundleId].components[handler.componentName] = {
          handlers: {},
          componentType: handler.componentType,
        };
      }
      bundles[handler.bundleId].components[handler.componentName].handlers[handler.handledPayload.name] = {
        messageType: handler.handledPayload.type,
        handlerType: handler.handlerType,
        uuid: handler.uuid,
      };
    }
    for (const handler of handlers) {
      const h = bundles[handler.bundleId].components[handler.componentName].handlers[handler.handledPayload.name];
      h.responseHandeledBy = [];
      h.invoke = [];
      if (handler.returnType) {
        for (const target of handlers.filter(hh => hh.handledPayload.name === handler.returnType.name
          && hh.handlerType !== 'EventSourcingHandler')) {
          h.responseHandeledBy.push(target.uuid);
        }
      }
      for (const invocation of Object.values(handler.invocations)) {
        // @ts-ignore
        for (const target of handlers.filter(hh => hh.handledPayload.name === invocation.name
          && hh.handlerType !== 'EventSourcingHandler')) {
          h.invoke.push(target.uuid);
        }
      }
    }

    // ---- Bubble size KPI: betweenness centrality over the handler relation
    // graph (invocations + response-handling as directed edges). A handler is
    // big when it sits on many of the shortest paths between other handlers —
    // i.e. it is a bridge/chokepoint of the application's message flow.
    const forEachHandler = (cb: (h: any) => void) => {
      for (const b of Object.values(bundles) as any[]) {
        for (const c of Object.values(b.components) as any[]) {
          for (const h of Object.values(c.handlers) as any[]) {
            if (h.handlerType !== 'EventSourcingHandler') {
              cb(h);
            }
          }
        }
      }
    };
    const relationEls: cytoscape.ElementDefinition[] = [];
    const known = new Set<string>();
    forEachHandler((h) => {
      known.add(h.uuid);
      relationEls.push({data: {id: h.uuid}});
    });
    let ri = 0;
    forEachHandler((h) => {
      for (const to of [...h.invoke, ...h.responseHandeledBy]) {
        if (known.has(to)) {
          relationEls.push({data: {id: `r${ri++}`, source: h.uuid, target: to}});
        }
      }
    });
    const ranks: Record<string, number> = {};
    {
      const tmp = cytoscape({headless: true, elements: relationEls});
      const bc = (tmp.elements() as any).betweennessCentrality({directed: true});
      tmp.nodes().forEach((n) => (ranks[n.id()] = bc.betweennessNormalized(n)));
      tmp.destroy();
    }
    const rankValues = Object.values(ranks);
    const minRank = Math.min(...rankValues);
    const maxRank = Math.max(...rankValues);
    const radiusForRank = (uuid: string) => {
      const span = maxRank - minRank;
      const t = span > 0 ? ((ranks[uuid] ?? minRank) - minRank) / span : 0.5;
      return 45 + t * 75; // 45px (peripheral) … 120px (most central)
    };

    // ---- Tight hierarchical circle packing via d3.pack. Leaf area encodes
    // the handler's centrality rank.
    const rootData: any = {
      children: Object.entries(bundles).map(([bundleId, b]: [string, any]) => ({
        kind: 'bundle',
        id: bundleId,
        children: Object.entries(b.components).map(([componentName, comp]: [string, any]) => ({
          kind: 'component',
          id: componentName,
          componentType: comp.componentType,
          children: Object.entries(comp.handlers)
            .filter(([, h]: [string, any]) => h.handlerType !== 'EventSourcingHandler')
            .map(([payloadName, h]: [string, any]) => ({
              kind: 'handler',
              id: h.uuid,
              name: payloadName,
              messageType: h.messageType,
              invoke: h.invoke,
              responseHandeledBy: h.responseHandeledBy,
              value: radiusForRank(h.uuid) ** 2,
            })),
        })).filter((c: any) => c.children.length),
      })).filter((b: any) => b.children.length),
    };

    // World size scales with content. pack() squeezes whatever it gets into
    // the given canvas, so a fixed 1600px world crushed large apps' handler
    // bubbles to a few px — their labels could never fit at any zoom. Derive
    // the canvas side from the total leaf area (times a packing-waste factor)
    // so bubbles keep their intended 45–120px radii regardless of app size.
    let totalLeafValue = 0;
    for (const b of rootData.children) {
      for (const c of b.children) {
        for (const h of c.children) {
          totalLeafValue += h.value;
        }
      }
    }
    const side = Math.max(1600, Math.round(4 * Math.sqrt(totalLeafValue)));

    const root = pack<any>()
      .size([side, side])
      .padding((d: any) => (d.depth === 0 ? side * 0.025 : d.depth === 1 ? side * 0.016 : 8))(
        hierarchy(rootData)
          .sum((d: any) => d.value || 0)
          .sort((a, b) => (b.value || 0) - (a.value || 0)),
      );

    this.searchIndex = root.leaves()
      .filter((l: any) => l.data.kind === 'handler')
      .map((l: any) => ({
        id: l.data.id,
        label: l.data.name,
        component: l.parent.data.id,
        bundle: l.parent.parent.data.id,
        norm: `${l.data.name} ${l.parent.data.id}`.toLowerCase(),
      }));

    // ---- Build Cytoscape elements at the packed positions.
    const p = this.palette();
    const elements: cytoscape.ElementDefinition[] = [];
    for (const d of root.descendants()) {
      const data: any = d.data;
      if (!data.kind) {
        continue; // synthetic root
      }
      const rawLabel = data.kind === 'handler' ? data.name : data.id;
      // Fit the label inside the circle's inscribed square (side ≈ 1.41r):
      // font size scales with radius and shrinks with label length; handler
      // labels are pre-wrapped at camelCase boundaries so breaks land between
      // words, not mid-word. Bundle labels scale with the bundle's radius so
      // the biggest containers stay readable at the overview zoom, where
      // component/handler labels are culled by `min-zoomed-font-size`.
      const tmw = d.r * 1.3;
      let fs: number;
      if (data.kind === 'handler') {
        fs = Math.max(6, Math.min(16, (1.3 * d.r) / Math.sqrt(Math.max(rawLabel.length, 1))));
      } else if (data.kind === 'component') {
        fs = Math.max(12, Math.min(28, d.r * 0.16));
      } else {
        fs = Math.max(14, Math.min(72, d.r * 0.15));
      }
      const label = data.kind === 'handler' ? this.camelWrap(rawLabel, tmw, fs) : rawLabel;
      const common = {
        position: {x: d.x, y: d.y},
        data: {
          d: d.r * 2,
          label,
          fs,
          tmw,
        } as any,
      };
      if (data.kind === 'bundle') {
        elements.push({
          ...common,
          classes: 'bundle',
          data: {...common.data, id: `b:${data.id}`, color: getColorForBundle(data.id), nav: `/application-flows?bundle=${data.id}`},
        });
      } else if (data.kind === 'component') {
        elements.push({
          ...common,
          classes: 'component',
          data: {...common.data, id: `c:${data.id}`, color: componentColor[data.componentType] || 'grey', nav: `/application-flows?component=${data.id}`},
        });
      } else {
        elements.push({
          ...common,
          classes: 'handler',
          data: {...common.data, id: data.id, color: payloadColor[data.messageType] || 'grey', nav: `/application-flows?handler=${data.id}`},
        });
      }
    }
    let ei = 0;
    for (const leaf of root.leaves()) {
      const data: any = leaf.data;
      if (!data.kind) {
        continue;
      }
      for (const to of data.invoke) {
        elements.push({data: {id: `e${ei++}`, source: data.id, target: to}, classes: 'invoke'});
      }
      for (const to of data.responseHandeledBy) {
        elements.push({data: {id: `e${ei++}`, source: data.id, target: to}, classes: 'response'});
      }
    }

    const renderingLoading = await this.loadingController.create({message: 'Rendering application graph...'});
    await renderingLoading.present();

    const cy = cytoscape({
      container: this.container.nativeElement,
      elements,
      layout: {name: 'preset', fit: true, padding: 24},
      minZoom: 0.05,
      maxZoom: 4,
      wheelSensitivity: 0.3,
      autoungrabify: true,
      boxSelectionEnabled: false,
      style: [
        {
          selector: 'node',
          style: {
            shape: 'ellipse',
            width: 'data(d)',
            height: 'data(d)',
            'background-color': p.surface,
            'background-opacity': 1,
            'border-width': 2,
            'border-color': 'data(color)',
            label: 'data(label)',
            color: p.text,
            'font-size': 'data(fs)',
            'text-wrap': 'wrap',
            // Break long camel-case payload names mid-word — they contain no
            // whitespace, so default wrapping would overflow the bubble.
            'text-overflow-wrap': 'anywhere',
            'text-max-width': 'data(tmw)',
            'text-valign': 'center',
            'text-halign': 'center',
            // Level-of-detail: skip any label whose on-screen size would be
            // below ~8px. On large applications this is what keeps the
            // overview readable — labels fade in as you zoom into a region
            // instead of all rendering at once as overlapping noise.
            'min-zoomed-font-size': 8,
            events: 'yes',
          } as any,
        },
        // Solid fills need explicit stacking: containers lowest, then the
        // relation edges (which would otherwise hide beneath filled circles),
        // handler bubbles on top. `z-index-compare: manual` is required —
        // the default (`auto`) always draws edges under nodes and ignores
        // z-index entirely.
        {selector: 'node, edge', style: {'z-index-compare': 'manual'} as any},
        {selector: '.bundle', style: {'z-index': 1} as any},
        {selector: '.component', style: {'z-index': 2} as any},
        {selector: 'edge', style: {'z-index': 3} as any},
        {selector: '.handler', style: {'z-index': 4} as any},
        // Hovered HANDLER bubbles (and highlighted relation endpoints) pop
        // above everything. Handler-only: bumping a hovered component/bundle
        // would lift its solid fill above the handler bubbles and hide them.
        {selector: '.handler.hl, .handler.hl-down, .handler.hl-up', style: {'z-index': 10} as any},
        {selector: 'edge.hl-down, edge.hl-up', style: {'z-index': 9} as any},
        {
          selector: '.bundle, .component',
          style: {
            'text-valign': 'top',
            'text-wrap': 'none',
            'text-background-color': p.surface,
            'text-background-opacity': 1,
            'text-background-padding': '3px',
            'text-background-shape': 'round-rectangle',
            'text-border-width': 1,
            'text-border-color': 'data(color)',
            'text-border-opacity': 1,
          } as any,
        },
        // Bundle names are the map's landmarks: keep them visible further out
        // than everything else (their font already scales with bundle size).
        {selector: '.bundle', style: {'min-zoomed-font-size': 5} as any},
        // A hovered node always shows its label, even when zoomed out past
        // the LOD cutoff — hover is the "what is this bubble?" gesture.
        {selector: 'node.hl', style: {'min-zoomed-font-size': 0} as any},
        {
          selector: 'edge',
          style: {
            'curve-style': 'straight',
            width: 1,
            'line-color': p.text,
            opacity: 0.08,
          } as any,
        },
        {selector: '.response', style: {'line-style': 'dashed'} as any},
        // Hover highlight, color-coded by direction: downstream (what this
        // handler triggers) in accent orange, upstream (what leads into it)
        // in teal-green. Edge-scoped selectors only — a bare class selector
        // would also match nodes and its `width` would collapse them.
        {
          selector: 'edge.hl-down',
          style: {opacity: 1, width: 2, 'line-color': p.down} as any,
        },
        {
          selector: 'edge.hl-up',
          style: {opacity: 1, width: 2, 'line-color': p.up} as any,
        },
        {
          selector: 'node.hl, node.hl-down, node.hl-up',
          style: {'border-width': 4} as any,
        },
      ],
    });
    this.cy = cy;

    // Bubble hover: enlarge the hovered handler and light up its downstream
    // path (the old view's BFS-over-outgoing-edges highlight). A search-pinned
    // handler keeps its highlight: its own mouseout is ignored, and other
    // bubbles' mouseouts re-apply the pinned paths they may have stripped.
    cy.on('mouseover', 'node', (evt) => {
      const n = evt.target;
      n.addClass('hl');
      if (n.hasClass('handler')) {
        this.reveal(n);
        n.successors().addClass('hl-down');
        n.predecessors().addClass('hl-up');
      }
    });
    cy.on('mouseout', 'node', (evt) => {
      const n = evt.target;
      if (n.id() === this.pinnedId) {
        return;
      }
      n.removeClass('hl');
      if (n.hasClass('handler')) {
        this.unreveal(n);
        n.successors().removeClass('hl-down');
        n.predecessors().removeClass('hl-up');
      }
      if (this.pinnedId) {
        const p = cy.getElementById(this.pinnedId);
        if (p.nonempty()) {
          p.successors().addClass('hl-down');
          p.predecessors().addClass('hl-up');
        }
      }
    });

    // Keep the pinned handler's revealed label at a readable on-screen size
    // while the user zooms around it.
    cy.on('zoom', () => {
      if (this.pinnedId) {
        const p = cy.getElementById(this.pinnedId);
        if (p.nonempty()) {
          this.reveal(p);
        }
      }
    });

    // Tapping empty background dismisses the search highlight.
    cy.on('tap', (evt) => {
      if (evt.target === cy) {
        this.clearSearch();
      }
    });

    cy.on('dbltap', 'node', (evt) => {
      const nav = evt.target.data('nav');
      if (nav) {
        this.navController.navigateForward(nav);
      }
    });

    this.resizeObserver = new ResizeObserver(() => {
      cy.resize();
      cy.fit(undefined, 24);
    });
    this.resizeObserver.observe(this.container.nativeElement);

    // Recolor labels/edges when the light/dark theme flips.
    this.themeObserver = new MutationObserver(() => {
      const np = this.palette();
      cy.style()
        .selector('node').style({color: np.text, 'background-color': np.surface} as any)
        .selector('.bundle, .component').style({'text-background-color': np.surface} as any)
        .selector('edge').style({'line-color': np.text} as any)
        .selector('edge.hl-down').style({'line-color': np.down} as any)
        .selector('edge.hl-up').style({'line-color': np.up} as any)
        .update();
    });
    this.themeObserver.observe(document.documentElement, {attributes: true, attributeFilter: ['class']});

    await renderingLoading.dismiss();
  }

  ngOnDestroy() {
    this.themeObserver?.disconnect();
    this.resizeObserver?.disconnect();
    this.cy?.destroy();
  }

  search() {
    const q = this.searchTerm.trim().toLowerCase();
    this.searchResults = q
      ? this.searchIndex.filter(e => e.norm.includes(q)).slice(0, 12)
      : [];
  }

  selectFirst() {
    if (this.searchResults.length) {
      this.select(this.searchResults[0]);
    }
  }

  select(r: {id: string; label: string}) {
    this.searchResults = [];
    this.searchTerm = r.label;
    const cy = this.cy;
    if (!cy) {
      return;
    }
    this.clearPin();
    const n = cy.getElementById(r.id);
    if (n.empty()) {
      return;
    }
    this.pinnedId = r.id;
    n.addClass('hl');
    n.successors().addClass('hl-down');
    n.predecessors().addClass('hl-up');
    // Fly to the handler: zoom in (never out) until its bubble takes up a
    // decent chunk of the viewport, then scale its label up to readable.
    const zoom = Math.max(cy.zoom(), Math.min(1.4, 260 / n.data('d')));
    cy.animate(
      {center: {eles: n}, zoom},
      {duration: 500, easing: 'ease-in-out-cubic', complete: () => this.reveal(n)},
    );
  }

  clearSearch() {
    this.searchTerm = '';
    this.searchResults = [];
    this.clearPin();
  }

  private clearPin() {
    if (!this.pinnedId || !this.cy) {
      return;
    }
    const n = this.cy.getElementById(this.pinnedId);
    this.pinnedId = null;
    if (n.nonempty()) {
      n.removeClass('hl');
      this.unreveal(n);
      n.successors().removeClass('hl-down');
      n.predecessors().removeClass('hl-up');
    }
  }

  /**
   * Enlarge a handler bubble and, when zoomed out past the LOD cutoff, scale
   * its label (font AND wrap width, so the pre-computed camelCase line breaks
   * hold) up to a readable on-screen size.
   */
  private reveal(n: any) {
    const dia = n.data('d');
    n.style({width: dia * 1.18, height: dia * 1.18});
    const k = 11 / (n.data('fs') * this.cy.zoom());
    if (k > 1) {
      n.style({'font-size': n.data('fs') * k, 'text-max-width': n.data('tmw') * k});
    } else {
      n.removeStyle('font-size').removeStyle('text-max-width');
    }
  }

  private unreveal(n: any) {
    n.removeStyle('width').removeStyle('height').removeStyle('font-size').removeStyle('text-max-width');
  }

  private palette() {
    return {
      surface: cssToken('--evento-surface', '#ffffff'),
      text: cssToken('--evento-text', '#14201f'),
      accent: cssToken('--ion-color-tertiary', '#DE7340'),
      down: cssToken('--ion-color-tertiary', '#DE7340'),
      up: cssToken('--ion-color-success', '#00C597'),
    };
  }

  /**
   * Wrap a camelCase identifier into lines that fit `maxWidth` at `fontSize`,
   * breaking ONLY at camelCase/namespace boundaries (greedy packing). Tokens
   * longer than a line are left intact — the renderer's overflow-wrap picks
   * those rare cases up as a fallback.
   */
  private camelWrap(label: string, maxWidth: number, fontSize: number): string {
    const maxChars = Math.max(4, Math.floor(maxWidth / (fontSize * 0.62)));
    if (label.length <= maxChars) {
      return label;
    }
    const tokens = label
      .replace(/([a-z0-9])([A-Z])/g, '$1\u0001$2')
      .replace(/::/g, '::\u0001')
      .split('\u0001');
    const lines: string[] = [];
    let current = '';
    for (const token of tokens) {
      if (!current || (current + token).length <= maxChars) {
        current += token;
      } else {
        lines.push(current);
        current = token;
      }
    }
    if (current) {
      lines.push(current);
    }
    return lines.join('\n');
  }
}
