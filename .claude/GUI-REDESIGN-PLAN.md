# Evento GUI — UX/UI Redesign Plan

Premium redesign of `evento-gui` (Angular 22 + Ionic 8) while **preserving the
existing brand**: color scheme, logo, and font. Plus three functional changes:
a real login (username/password → server token), a graph engine swap
(mxGraph → Cytoscape.js), and a pivot to an **explorative / read-only** server
(only the consumer dead-queue replay stays interactive).

Decisions locked with the owner:
- **Login**: static username + password over **HTTP Basic Auth** (replaces the
  JWT/Bearer scheme entirely). The server validates a configured static user via
  Spring Security Basic auth; the frontend sends `Authorization: Basic <base64>`.
- **Graph library**: **Cytoscape.js** with the **elk** layout (+ fcose, popper).
- **Font**: **properly load Inter** for UI type, **and keep Roboto** — Roboto is
  used in the logo/wordmark, so it stays for brand continuity (nav/brand).
- **Logo**: **keep the PNG** as-is (no SVG conversion).
- **Dark mode**: **in scope** — full light + dark themes.

---

## Brand invariants (must be preserved)

| Token | Value | Notes |
|---|---|---|
| Primary | `#002121` | dark teal — header/nav |
| Secondary | `#2D4D4D` | slate teal |
| Tertiary / accent | `#DE7340` | warm orange — the one accent |
| Success | `#00C597` | teal-green |
| Medium | `#95B1B0` | muted grey-teal |
| Light / cream | `#F1F1E6` | warm neutral |
| Logo | `src/assets/img/logo.png` | keep PNG as-is |
| Font (UI) | **Inter** | currently unloaded; we will load it |
| Font (logo/brand) | **Roboto** | used in the logo — keep it loaded |

Plus the **domain color legend** in `global.scss` that diagrams depend on
(payload types: DomainCommand `#3399fe`, ServiceCommand `#ff68b9`, DomainEvent
`#ff992a`, ServiceEvent `#cb3234`, View `#00CC00`, Invocation grey, Query
`#FFD700`; component types: Aggregate blue, Service red, Projection lightgreen,
Projector `#008000`, Saga purple, Observer black, Invoker gray). These become
tokens with **dark-mode-adjusted** variants (same hue, tuned lightness).

---

## Phase 1 — Design system foundation

Goal: a token layer that makes everything else consistent and themeable.

- Extend `src/theme/variables.scss` beyond colors with **design tokens**:
  spacing scale (4/8/12/16/24/32…), radius scale (sm/md/lg), elevation/shadow
  tokens, typography scale (font sizes + line-heights + weights), motion
  (durations/easings), z-index scale.
- **Fonts**: self-host **Inter** (via `@fontsource/inter` or local woff2) as the
  primary UI font, wire `--ion-font-family` + body/heading families to it.
  **Keep Roboto** — it matches the logo/wordmark; retain it for the nav/brand
  surfaces. Fix the *duplicate* Roboto `@import` (dedupe to one clean load; keep
  a lean weight subset). Define a type scale (display/h1/h2/body/caption/mono)
  with Inter for text and Roboto reserved for brand/nav.
- **Dark mode**: implement Ionic's CSS-variable dark palette. Support both
  `@media (prefers-color-scheme: dark)` and a manual `data-theme` override
  (persisted). Define dark surface/background/text tokens and the dark variants
  of the domain color legend. Fix `index.html`’s `color-scheme` to match.
- **Cleanup debt**: remove dead Tailwind-style vars (`--tw-*`), dead `ion-menu`
  CSS in `app.component.scss` (no menu exists), duplicate imports; begin moving
  inline template styles into classes.
- **Logo**: **keep the existing PNG** (`assets/img/logo.png`) as-is — no SVG
  conversion. For dark mode, if the current PNG doesn’t read on dark surfaces,
  add a dark-friendly **PNG variant** (or a subtle container/backing), swapped by
  theme. Serve at appropriate density (e.g. 2x) for crispness; mark/wordmark
  unchanged.

Acceptance: token file drives spacing/radii/type; Inter renders; light+dark
switch works globally; no visual regressions to brand colors.

---

## Phase 2 — App shell & navigation

Goal: an accessible, premium chrome; fix latent nav bugs.

- Replace the **fake `<div class="tab-button">` nav** (manual `selectedTab`,
  hardcoded initial `payload-catalog`, no roles/keyboard) with an **accessible
  router-driven nav**: real links/buttons, `aria-current`, keyboard focus, active
  state derived from the router (fixes the initial-tab bug). Active state = accent
  orange pill/underline.
- Premium top bar: refined height/spacing, subtle elevation + backdrop blur,
  brand logo (SVG), light/dark toggle, and a **logout** control (Phase 3).
- Responsive: collapse the nav into an overflow/menu on narrow widths.
- Refine `wrapped-content` layout primitive: consistent max-width, gutters,
  vertical rhythm from Phase 1 tokens.
- Global polish primitives: **skeleton loaders** (replace ad-hoc spinners),
  page/route transitions, consistent empty & error states.

Acceptance: keyboard-navigable nav with correct active tab on every route;
shell looks premium in light and dark; no `selectedTab` bug.

---

## Phase 3 — Login (username/password over HTTP Basic Auth)

Goal: replace the `prompt('Access token:')` + Bearer/JWT hack with a real,
guarded login backed by **HTTP Basic Auth**.

Current reality to remove: `src/main.ts` monkey-patches `window.fetch` to inject
`Authorization: Bearer <token>` from a `prompt()`, stored as
`localStorage['evento_server_web_token']`; `401/403` clears it and reloads. SSE
passes the token as a `?token=` query param.

### Backend (`evento-server`) — switch to Basic Auth
- Replace the JWT/Bearer security config with **Spring Security HTTP Basic
  auth** against a **static configured credential** (in-memory user; username +
  password from server config/env). No token minting, no `/api/auth/login`
  endpoint needed — Basic auth is validated per request.
- All `/api/**` endpoints require Basic auth; `401` with
  `WWW-Authenticate: Basic` on failure. Ensure CORS allows the `Authorization`
  header for the GUI origin.
- **SSE caveat:** `EventSource` cannot send an `Authorization` header. Options
  (decide in build): (a) allow the cluster-status SSE endpoint to accept
  credentials via a short-lived query param the server maps to the same static
  user, or (b) keep that one endpoint readable with a lightweight scheme. Keep
  the choice isolated to the SSE endpoint.

### Frontend (`evento-gui`)
- **Login page** (branded, premium): username + password form, validation,
  loading + error states.
- **`AuthService`**: capture credentials → compute `Basic <base64(user:pass)>` →
  **verify** by probing a protected endpoint (e.g. `GET /api/dashboard` or a
  dedicated `/api/auth/whoami`) with the Basic header; on `200` store the encoded
  credentials (localStorage/session), on `401` show an error. Expose reactive
  auth state; `logout()` clears creds + routes to `/login`.
- **Route guard** (functional `CanActivate`) on all app routes → redirect to
  `/login` when unauthenticated.
- **Centralized header injection**: replace the `window.fetch` monkey-patch with
  a single `apiFetch` helper (or migrate services onto it) that injects
  `Authorization: Basic …` from `AuthService`; on `401/403` → logout → `/login`
  (no full-page reload + prompt). Update the SSE call to use the chosen
  credential-passing scheme instead of `?token=`.
- **Logout** button in the header (Phase 2).

Acceptance: unauthenticated users hit `/login`; valid creds unlock the app and
every call (incl. SSE) authenticates via Basic auth; invalid creds show an error;
logout works; no `prompt()` and no Bearer/JWT remain.

---

## Phase 4 — Explorative / read-only pivot

Goal: the UI reflects a read-only server, keeping exactly one interactive
capability.

- **Remove** the only server-mutating write affordance: bundle **“Unregister”**
  (`DELETE /api/bundle/{id}`) — button in `bundle-info.page.html` +
  `BundleService.unregister()`.
- **Keep** the consumer **dead-queue replay** on Cluster Status → Consumers
  (`app-consumers`): retry toggle (`PUT …/event/{seq}?retry=`), delete dead event
  (`DELETE …/event/{seq}`), reprocess queue (`POST …/consume-dead-queue`). Polish
  its UX (clear affordances, confirmations, feedback toasts).
- Note: the **application-flows performance editors** (mean service time /
  invocation frequency) mutate only the **client-side** what-if model, not the
  server — these stay (they’re analysis, not server interaction).
- Audit pass to confirm no other write affordances leak into the UI.

Acceptance: no server-state mutation possible except the consumer dead-queue
actions; unregister gone; explorative intent clear.

---

## Phase 5 — Graph engine: mxGraph → Cytoscape.js

Goal: drop the deprecated global-script mxGraph (`4.2.2`) for a lighter, faster,
maintained engine, at visual parity or better, across all **5 views**.

Setup:
- Add `cytoscape` + `cytoscape-elk` (ELK layered layout, with orthogonal edge
  routing) for the hierarchical views, `cytoscape-fcose` for compound/nesting
  layout, `cytoscape-popper` for tooltips.
- Remove `mxgraph`, `@typed-mxgraph/typed-mxgraph`, `src/assets/js/mxgraph.conf.js`,
  the `angular.json` mxGraph script/asset/typeRoot entries, and `svg-pan-zoom`
  (Cytoscape has built-in pan/zoom).

Shared infra:
- A reusable **`GraphCanvasComponent`** (or `CytoscapeService`) encapsulating:
  init, stylesheet built from Phase-1 tokens (dark-aware), wheel-zoom, drag-pan,
  fit/center, double-click drill-down navigation, hover-highlight (BFS over
  neighbors), repo-link context menu, and tooltips. Replaces the per-component
  `declare const mxGraph` duplication and the `setTimeout(500)` init hacks.
- Reuse the two **pure-JS algorithms unchanged**: the queueing-network/MVA
  solver (flows) and, if we keep packing, the circle-packing math.

Per-view migration (data models are already known; consumed as `any` today):
- **A. Application Graph** (`ApplicationGraphDiagramComponent`, `/application-graph`):
  bundle→component→handler nesting. Prefer **compound nodes** (parent/child) +
  `fcose` for clean nesting; fallback = reuse existing circle-packing to preset
  positions. Edges: invocation (solid), response (dashed). Hover BFS highlight;
  dbl-click → `/application-flows?…`.
- **B. Invokers/Handlers** (`payload-info`): payload-centric star; elk layered (LR);
  in-edges (returnedBy/invokers), out-edges (subscribers).
- **C. Component Handlers** (`component-info`): elk layered (LR).
- **D. Bundle Components** (`bundle-info`): elk layered (LR).
- **E. Application Flows** (`/application-flows`): elk layered **LR/TB toggle**
  (replaces mx `west/north`). Node shapes: gateway/source = round-rect,
  event-store = **barrel/cylinder** (bg SVG), sink = ellipse. Performance overlay:
  edge width/color via `perc2color`, edge labels (throughput/queue/%), bottleneck
  stroke, per-node service-time/customers. **Reuse the existing solver.**
  Animated dashed “flow” edges via Cytoscape edge animation / line-dash. Context
  menu: repo link + perf editors.

Preserve across all: read-only, wheel zoom, pan, fit/center, dbl-click nav, repo
context menu, animated flow edges, and the domain color legend (now token-driven
and dark-aware).

Acceptance: all 5 views render at parity or better in light+dark; faster init
(no 500ms delay); zero mxGraph globals; drill-down, tooltips, repo links, and
flows performance mode all work.

---

## Phase 6 — Data tables & content polish

Goal: replace the hand-rolled `ion-grid` “tables” and lift the catalog pages to
premium.

- Build a reusable **data-table component** (semantic `<table>`/roles, sticky
  aligned headers, zebra/hover, optional sort, responsive) and adopt it in the
  four fake tables: **event store**, **snapshot store**, **consumers dead-queue**,
  **cluster status**. Keep infinite scroll / pagination / pull-to-refresh.
- **Catalog card grids** (bundle-list, component-catalog, payload-catalog,
  flows rail): refined cards (subtle border + elevation, hover lift), consistent
  chips, accent usage, skeletons.
- Detail pages: premium spacing, consistent chip rails, refined `markdown` and
  JSON `<pre>` blocks (wrapping controls, copy button).

Acceptance: aligned, accessible tables with sticky headers; catalog/detail pages
visually premium in both themes.

---

## Cross-cutting (folded through all phases)

- **Accessibility**: cards-as-buttons → real buttons (role/tabindex/keyboard);
  color-only status → add icon/text; alt/aria on logo/icons; focus-visible states
  in accent orange.
- **i18n**: externalize hardcoded English (`consumers.component.html` health
  labels, telemetry control labels, “Open in flows →”) into `en.json`. (i18n was
  just fixed in Phase 0 — see `app.module.ts` `lang/fallbackLang`.)
- **Dedup/cleanup**: consolidate near-identical `component-telemetry` /
  `aggregate-telemetry`; fix raw `href` → `routerLink` in
  `invokers-handlers-diagram`; delete commented-out dead markup.

---

## Sequencing, risk, effort

Suggested order & dependencies:
1. **Phase 1 (foundation)** — unblocks everything (tokens, Inter, dark mode).
2. **Phase 2 (shell)** — depends on 1.
3. **Phase 3 (login)** — mostly independent; **needs backend endpoint confirm**.
4. **Phase 4 (read-only)** — small, independent; can land early.
5. **Phase 5 (Cytoscape)** — largest; depends on 1 (tokens/dark palette).
6. **Phase 6 (tables/polish)** — depends on 1–2.

Ship as separate PRs (Conventional Commits). Each phase is independently
releasable and testable.

Risk hotspots:
- **Phase 5** is the heavy lift — especially view A (nesting) and view E
  (perf overlay + animated edges). Mitigate by reusing the pure-JS algorithms and
  landing views incrementally (B/C/D first, then A, then E).
- **Phase 3** spans **both repos** — the `evento-server` security swap
  (JWT/Bearer → Basic auth) plus the GUI login. Land the server change first (or
  in lockstep) so the GUI can authenticate. The **SSE + Basic auth** gap is the
  sharp edge — decide the credential-passing scheme up front.
- Dark-mode contrast for the fixed domain color legend needs tuning to stay
  legible on dark surfaces.

Effort (rough, relative): P1 M · P2 M · P3 M (+backend S) · P4 S · P5 L · P6 M.

---

## Open items to confirm before build

1. **Basic Auth credentials**: where do the static username/password live in
   `evento-server` (application config / env vars)? And should I implement the
   Spring Security Basic-auth swap in the server, or will you?
2. **SSE + Basic auth**: `EventSource` can’t send an `Authorization` header —
   pick the scheme for the cluster-status SSE endpoint (short-lived query-param
   credential vs. leaving that one endpoint open). 
3. **Verify endpoint**: use an existing GET (e.g. `/api/dashboard`) to validate
   creds at login, or add a tiny `/api/auth/whoami`?

Resolved: **Basic Auth** (not JWT) · **keep Roboto** (used in logo) · **keep logo
PNG** · **elk** layout.
