# Evento Framework — status snapshot

Last updated: 2026-07-24. Branch `next` merged to `main`; v2.0 rewrite complete.
`evento-cli` **and** `evento-parser` modules deleted; deployment/autoscaling surface removed.

## Both screenshot-session bugs closed (2026-07-24)

The two bugs recorded on 2026-07-23 during the docs screenshot session are resolved:

- **Server: rolling restarts wiped live bundle registrations — FIXED** (`fix(server)`
  commit on main). Root cause was three cooperating defects, worse than the originally
  suspected registration/discovery race: (1) the bundle row records the instance that
  auto-registered it and `onNodeLeave` reclaims the whole registration (handlers,
  components, consumers, bundle) when that instance departs, but `onNodeJoin` never
  transferred that ownership — so during a rolling restart (replacement joins before the
  old socket dies) the stale departure deleted the rows the live instance had just
  registered; (2) `onNodeLeave` locked `DISCOVERY:<instanceId>` while `onNodeJoin` locks
  `DISCOVERY:<bundleId>`, so leave could interleave with join for the same bundle; (3)
  `ConsumerService.registerConsumers` built `Consumer` rows from `getReferenceById` lazy
  proxies — a missing component row blew up the flush (`ObjectNotFoundException
  'OrderObserver'`) and the outer catch silently dropped the whole registration. Fixes:
  ownership transfer on join (null `instanceId` still marks manually published bundles,
  never reclaimed), per-bundle lock + live-instance guard on leave, and up-front
  component resolution with ephemeral recreation in `registerConsumers` (consumer maps
  are keyed by component name, type implied by the map; discovery backfills metadata).
  Regression tests: `AutoDiscoveryServiceLifecycleTest` (drives the service through the
  captured bus subscriber), `ConsumerServiceRegistrationTest`. The
  `ObjectOptimisticLockingFailureException` on payload deletes during concurrent
  leave/join should also disappear now that leave/join serialize per bundle.
- **GUI: `/component-catalog` and `/bundle-list` "render skeletons forever" — NOT A
  BUG.** Reproduced, then root-caused as an automation-environment artifact: the
  screenshot sessions ran in a Chrome window that Windows marked occluded (display
  asleep), so `document.visibilityState === 'hidden'` and Chrome stops servicing
  `requestAnimationFrame`. Ionic's tab transition is rAF-driven, so `ionViewWillEnter`
  never fires while hidden and the page sits on "(0)" skeletons; `/payload-catalog` only
  "worked" because its fetch had happened while the window was still visible. In a
  visible browser (headless Chrome over CDP against the same production build) full
  load, SPA nav to both pages, and revisits all populate correctly; no app change
  needed. Real users are unaffected (a hidden tab resumes when it becomes visible).
  The two remaining stale doc screenshots (`image (54).png`, `image (57).png`) were
  re-captured headless from the real 2.3.1 stack and pushed to evento-doc. Lesson for
  future browser automation on this box: the display sleeps while the user is away —
  drive the GUI with headless Chrome (`--headless=new --remote-debugging-port`) instead
  of the desktop Chrome extension, or the tab throttles.

## Prod hang: unbounded channel-close wait wedged bus workers (2026-07-22, latest)

A production evento-server 2.2.1 (market deployment) stopped serving event fetches for 3 days:
`bus-business` workers were stuck forever in `DefaultPromise.awaitUninterruptibly` under
`ServerChildTransport.close()` ← `ConnectionRegistry.register()` (the `hello_will_supersede`
path). The close promise of the half-dead superseded channel was never completed, so the worker
blocked indefinitely; leaked workers took open DB transactions (`idle in transaction` on
`es__events`) and Hikari connections with them → pool (default 10) exhausted → every client
`fetchEvents` failed remotely with `HikariPool-1 - Connection is not available`. Full thread dump
from the incident: market prod server `/opt/market/backups/evento-server-threaddump-20260722.txt`.

- **Fix (this session):** `Transport.close()` no longer waits unboundedly — both
  `NettyServerTransport$ServerChildTransport.close()` and `NettyClientTransport.close()` use
  `close().awaitUninterruptibly(connectTimeout)` and log `child_close_timeout` /
  `client_close_timeout` on expiry (close op stays scheduled; we just stop waiting). Regression
  IT `childCloseReturnsWhenEventLoopIsWedged` wedges the child's event loop with a latch and
  asserts close returns bounded (old code hangs → caught by `@Timeout`).
- `ConnectionRegistry.register` keeps its synchronous close (ConnectionRegistryTest asserts the
  superseded transport is terminal on return); worst case is now a bounded `connectTimeout`
  stall, not a permanent wedge.
- **Open question:** WHY the close promise never completed is still not root-caused (dump shows
  wedged workers, but the event-loop-side stack wasn't captured). If it recurs, dump BOTH
  bus-business and `nioEventLoopGroup` threads.
- **Ops mitigation applied on the market prod DBs** (not framework): postgres
  `idle_in_transaction_session_timeout='5min'` on both the evento and market databases.

## Consumers tab dead since v2 — listener never wired (2026-07-22, later)

"Cluster Status → Consumers is blank" turned out to be a framework bug, not a GUI one:
**`BundleAdminNotificationListener` was never instantiated on any v2.x server**, so consumer
registrations AND all bundle performance telemetry were silently dropped since the v2 rewrite.
Commits `2e51daa5` (server) + `04cf632c` (bundle) + `e65e8844` (gui). Verified live against the
full multi-bundle deployment (11 bundles, evento-bundle 2.2.1): consumers now appear.

- **Two independent DI kill-switches** on the listener: (1) `@ConditionalOnProperty` still used
  the pre-rename `evento.server.bus.v2.enabled` prefix nothing sets; (2)
  `@ConditionalOnBean(BusLifecycle.class)` on a component-scanned class evaluates during the
  scan, before `@Configuration` `@Bean` definitions register → deterministically false (Spring
  restricts `@ConditionalOnBean` to auto-configs for this reason; it was redundant anyway).
  Wire-level ITs stayed green because they subscribe to `BusEventBus` directly, "no Spring" by
  design — new `BundleAdminNotificationListenerWiringTest` (ApplicationContextRunner + real
  component scan) closes that blind spot.
- **Destructive GET removed:** `ConsumerController.findAllConsumers` used to `clearInstance()`
  rows whose instance wasn't currently available — listing consumers during a bundle reconnect
  permanently wiped registrations. Now a pure read; cleanup lives in
  `AutoDiscoveryService.onNodeLeave`.
- **Bundle re-registers on reconnect:** consumer registration was one-shot at startup; now also
  re-sent on every transition to READY (idempotent upsert server-side). Old 2.2.x bundles still
  need a process restart after a server DB wipe.
- **Debug techniques that cracked it:** `jcmd <pid> GC.class_histogram | grep <Bean>` proved the
  bean absent in the live JVM; `git diff v2.2.1..HEAD -- <wire classes>` proved 2.2.1↔HEAD wire
  compat; classpath + `build\classes` timestamps proved the running process had the "fixed" jar.

## GUI graph readability at scale (2026-07-22)

Field-tested the GUI against a big real application (~10 bundles, hundreds of
handlers) and fixed the two graph views that fell apart at that size. Commits
`127b571d` (feat) + `bf3bcd0d` (fix), both `evento-gui` only.

- **Application Graph unreadable (text soup):** every label rendered at every zoom,
  and d3-pack squeezed the world into a fixed 1600px canvas so handler bubbles
  collapsed to a few px. Now: `min-zoomed-font-size` culls labels under ~8 on-screen
  px (they fade in per region as you zoom — bundle names scale with radius and act as
  overview landmarks); the pack canvas side derives from total leaf area
  (`4·√Σr²`, floor 1600) so bubbles keep their intended 45–120px radii at any app
  size; container fonts/padding scale along.
- **Handler search in the Application Graph:** floating type-ahead (matches payload +
  component name, shows `payload — component · bundle`), Enter/click flies to the
  handler and **pins** the hover highlight (enlarged bubble, label forced readable at
  any zoom via font+wrap-width scaling, downstream orange / upstream teal paths).
  Dismiss: background tap, searchbar clear, or Escape. Hover logic refactored into
  pin-aware `reveal`/`unreveal` helpers. New i18n key `application.graph.search`.
- **Flows view blurry after zoom on big flows** (`evento-graph.ts`, so also bundle/
  component diagrams): root cause was the marching-ants RAF loop — restyling all
  `.flow` edges every frame makes Cytoscape invalidate its texture caches
  (`onUpdateEleCalcs` → "any change invalidates the layers"), which permanently
  starves the progressive re-rasterization that sharpens nodes/labels after a zoom.
  Fix: ants pause during viewport changes + 800ms after (idle frames let caches
  refine; resuming only touches edge caches), and flows with **>500** flow edges skip
  the animation entirely (static dashes).
- Gotcha worth remembering: any per-frame `eles.style(...)` tick on a Cytoscape graph
  silently disables its texture-cache refinement — throttle/pause it or big graphs
  stay low-res forever.
- Verified via `ng build --configuration production` (output lands in
  `evento-server/src/main/resources/public/`). Not yet visually re-verified on the
  full-size app beyond the pre-fix screenshot — worth a quick look next session.

## GUI redesign (2026-07-15 → 18) — plan in `.claude/GUI-REDESIGN-PLAN.md`

Premium UX/UI pass on `evento-gui` preserving the brand (colors/logo/fonts).
**All phases (1–6) done and on `main`.**

- **P1 foundation**: `--evento-*` design tokens (spacing/radius/shadow/type/motion),
  self-hosted Inter + Roboto (@fontsource), full light/dark theming
  (`ThemeService` signals, `ion-palette-dark`, persisted toggle).
- **P2 shell**: accessible router-driven header nav (aria-current, keyboard),
  skeletons, empty states.
- **P4 read-only pivot**: bundle Unregister removed; consumer dead-queue replay
  is the only remaining server mutation.
- **P5 graphs — mxGraph fully removed, replaced by Cytoscape.js + dagre**.
  Shared engine `components/graph/evento-graph.ts` (per-connected-component
  dagre layout + stacking, mx-style elbow edges with distributed ports and
  24px jetties, marching-ants flow edges, context menus, theme-reactive).
  Application graph rebuilt on d3 circle-packing (betweenness-centrality
  sizing, hover grow + up/downstream color highlight). Event-store node keeps
  the legacy database-cylinder shape (inline SVG). Hard-won gotchas (autolock
  vs layouts, z-index-compare manual, elkjs-worker-under-webpack broken)
  recorded in commit bodies.
- **P6 tables/polish**: shared `.evento-table` styles (semantic `<table>`,
  sticky headers, zebra hover, plain variant); adopted in event store,
  snapshot store, consumer dead-queue, cluster-status replica grids. Shared
  per-table detail modals replace per-row modal instances. JSON `.code-block`
  with copy button. App-wide sweep: ngx-translate **directive on Ionic web
  components duplicates text on re-slot** → converted to translate pipe
  everywhere (directive is safe only on native elements).

- **P3 login (both repos)**: server's JWT web stack
  (`AuthFilter`/`AuthService`/`TokenRole`/`AuthController`, `security.mode`,
  auth0 java-jwt dep) **deleted**; `WebConfig` now does always-on **HTTP Basic**
  over `/api/**` + actuator (health/info public) against Spring Boot's in-memory
  user (`spring.security.user.name/password/roles=WEB,ADMIN`, env-overridable;
  defaults evento/secret). Plain 401 without `WWW-Authenticate` so the browser's
  native popup never fights the GUI login page; stateless sessions. Controllers'
  `@Secured` annotations unchanged (static user carries both roles). GUI: the
  `window.fetch` monkey-patch + `prompt()` token flow is gone — `services/api.ts`
  `apiFetch()` injects `Authorization: Basic` from localStorage and hard-redirects
  to `/login` on 401/403; `AuthService.login()` verifies creds by probing
  `GET /api/dashboard`; functional `authGuard` on all routes; branded `/login`
  page; logout button in header; nav hidden while unauthenticated. Cluster-status
  SSE now consumed via **fetch + ReadableStream** (EventSource can't send an
  Authorization header; the old `?token=` query param leaked into access logs).

The repo remote moved: `origin` now points at
`git@github.com:EventoFramework/evento-framework.git`.

## Dependabot upgrade sweep (2026-07-12)

Triaged and landed the open Dependabot backlog on `main`. Two findings shaped the work:
CI (`ci.yml`) only runs the Gradle/Java suites — **the `evento-gui` frontend is never built
in PR CI** (only in `release.yml` via `ionic build --prod`), so green checks on frontend PRs
meant nothing; and `main` requires 1 approval + strict up-to-date checks, so bot PRs were
merged with `gh pr merge --squash --admin`.

- **Merged directly** (patch/minor + CI-validated majors): flyway, setup-java, codeql-action,
  log4j-api, assertj, awaitility, capacitor cli/core, ionic/angular, HikariCP 7, okhttp 5,
  hibernate-validator 9, junit-platform-launcher 6, action-gh-release 3.
- **`chore/frontend-upgrades` → #154**: coordinated **Angular 21 → 22 + TypeScript 5.9 → 6**
  via `ng update` (Angular can't move one package at a time), plus zone.js 0.16, ngx-markdown 22
  (+ `marked-katex-extension`, a lazy dynamic import esbuild must resolve). TS 6 / Angular 22
  turn strict checks on by default — the app has always been non-strict, so that posture is
  pinned in `tsconfig.json` rather than retyping ~350 sites; angular-eslint 22's new opinionated
  rules opted out. Superseded piecemeal PRs #142/#145/#146/#151/#152/#153. **eslint 10 (#148)
  deferred** — `eslint-plugin-import@2.32` has no eslint-10 peer support (needs `eslint-import-x`).
  Validated with `ionic build --prod` + `ng lint` on Node 24 (nvm).
- **`chore/spring-boot-4` → #155**: **Spring Boot 3.5.5 → 4.1.0** (consolidates plugin bump #137
  + BOM bump #134). Actuator health types moved to `org.springframework.boot.health.contributor.*`.
  **BREAKING (TLS):** SB4's BOM pulls **Netty 4.1 → 4.2**, which enables hostname verification by
  default; the client `SslHandler` was built with no peer identity, so all TLS handshakes failed.
  `NettyClientTransport` now threads remote host/port into `EventoPipelineFactory` →
  `newHandler(alloc, host, port)` (SNI + verification against the broker host). Operators using TLS
  must now present a cert whose CN/SAN matches the connect host.
- **#135/#136** (actions/checkout 7, attest-build-provenance 4): merged after granting the `gh`
  token the `workflow` scope. **All 25 Dependabot PRs resolved; queue empty.**

### Pipeline hardening follow-up (#156)

- **Frontend now built in PR CI** — new `gui-build` job in `ci.yml` mirrors `release.yml`
  (`npm install` + `ionic build --prod`) plus `ng lint`. Previously the GUI was only built at
  release time, so frontend dep bumps merged green without validation. **Not yet a required
  check** — add `gui-build` to branch protection to make frontend regressions block PRs.
- **Weekly Fuzz job fixed** (failed every run since June): `CborCodecFuzzTest` found malformed
  CBOR that made `JacksonCborCodec.decode` leak a raw `NullPointerException` from Jackson. `decode`
  only caught `IOException`; broadened to normalize any `RuntimeException` from the parse into
  `CodecException`. Reproducer committed as a Jazzer regression seed under
  `src/test/resources/.../CborCodecFuzzTestInputs/<method>/` (per-PR `:test` replays it). Verified:
  regression fails without the fix, passes with it; 40.8M-exec `JAZZER_FUZZ=1` run clean.

### Supply-chain / Scorecard remediation (2026-07-12) — PR #157

Attacked the two OpenSSF Scorecard checks holding the aggregate at **7.4/10**, both
rooted in `evento-gui`.

- **Vulnerabilities (0/10):** OSV flagged **21** advisories, all transitive npm deps of the
  Angular/Ionic toolchain. Root cause is shared with Pinned-Dependencies: CI resolved with
  `npm install` (ignores the lockfile), so the tree drifted into vulnerable versions. Fixed via
  `npm audit fix` + an `overrides{}` block pinning the stubborn leaves (`@babel/core ^7.29.7`,
  `esbuild ^0.28.1`, `http-proxy-middleware ^3.0.7`, `webpack-dev-server ^6.0.0`). `npm audit`
  drops **16 (21 per OSV) → 1**: only `mxgraph` setTooltips XSS (GHSA-j4rv-pr9g-q8jv) remains —
  unmaintained, **no patched version in any release**, tracked as accepted risk (future migration
  to `@maxgraph/core` would clear it).
- **Pinned-Dependencies (7/10) + 63 code-scanning alerts:** regenerated `package-lock.json` with
  npm 11 so it records **every** platform's optional native deps (`@esbuild/linux-x64`,
  `@rollup/rollup-linux-x64`, `@lmdb/*`) — this is what the old macOS/arm64 lockfile omitted and
  why `npm ci` failed on Linux. With that fixed, `ci.yml`/`release.yml` switch `npm install` →
  **`npm ci`**, drop the unpinned global `npm install -g @ionic/cli`, and build via `npm run build`
  (`ng build`). Verified on Node 24: `npm ci` + `ng lint` + prod build all green.
- **CSRF code-scanning alert (#1, High) dismissed** as "won't fix": the server is a stateless
  REST API authenticated by a JWT **bearer token in the Authorization header** (`AuthFilter`) — no
  session cookies / form login — so `csrf(disable)` in `WebConfig` is correct; CSRF defends
  cookie/session auth only.
- **Still capping the score (out of repo-code control):** Code-Review (0) and Contributors (0) are
  structural for a solo maintainer — Scorecard only counts approvals from *other* people.
  Branch-Protection (5) wants `enforce_admins` on + 2 reviewers + CODEOWNERS; CII-Best-Practices (2)
  needs the bestpractices.dev questionnaire completed. Vulnerabilities + Pinned wins alone should
  push the aggregate over 8.
- **Deflaked `BusLifecycleDisconnectIT` (2026-07-13):** PR CI intermittently red on this real-TCP
  server IT — two of its tests timed out at the initial request round-trip (handler hadn't seen the
  routed `Request` within a 3s `CountDownLatch` budget). Failure was pure CI-load timing (the PR is
  npm/workflow-only, no server code; passes deterministically on a dev box). Every per-step timeout
  now uses a shared `STEP_TIMEOUT_SECONDS = 10` constant and the class `@Timeout` went 30s → 60s —
  a ceiling, not an expected latency; genuine hangs still trip the class timeout.
- **PR #157 merged to `main` via `--admin`** (2026-07-13): all 5 checks green; branch protection's
  1-review requirement is structural for a solo maintainer (see Code-Review/Contributors above), so
  squash-merged with admin override, consistent with the Dependabot sweep.

## Consumer resilience + JDBC schema fixes (2026-07-03) — released as 2.1.1

Five commits on `main` (`884ddac5`..`0222db14`), version **2.1.1** (renumbered from 2.1.3 — patch +1 over last published 2.1.0; 2.1.1/2.1.2/2.1.3 local builds were never published):

- **`feat(consumer)` — transient failures redeliver instead of dead-lettering:** new
  `TransientConsumerException` (evento-common); `ConsumerProcessor.isTransient(...)` walks the
  cause chain (class names + high-signal messages, no transport/JDBC imports) and, for a downed
  collaborator / timeout / refused-reset connection, leaves the checkpoint in place and throws the
  typed signal instead of DLQ-ing. `SagaEngine`/`ProjectorEngine` treat it like a channel error →
  exponential backoff. Permanent failures still dead-letter (poison-pill protection).
  `SagaUnderDownedDependencyTest` covers liveness, checkpoint preservation, DLQ for permanent
  failures, exactly-once after recovery, and documents the idempotency hazard for side effects
  placed before the failing step.
- **`fix(jdbc)` — Flyway baseline bug:** on a non-empty schema, default baseline 1 silently skipped
  `V1__init_v2_consumer_state` ("no migration necessary", tables never created). Now
  `baselineVersion("0")`. Regression IT `FlywayMigratorNonEmptySchemaIT` (Testcontainers, gated on
  `EVENTO_RUN_JDBC_IT=true`).
- **`feat(jdbc)` — auto-migrate:** `JdbcConsumerStateStore` runs `FlywayMigrator.migrate` lazily on
  first connection (once per store, retryable on failure); new `autoMigrate` constructor flag
  (default `true`) to opt out when the schema is managed externally.
- **`feat(bundle)` — fail fast on field injection:** `ComponentManager` rejects
  `@Autowired`/`@Inject` fields at instantiation (detected by annotation name, no new deps) with an
  actionable error pointing to constructor wiring, instead of a late NPE.

- **`fix(bundle)` — ASM scan crashed on null literals (`51fc2cc1`):** `AsmInvocationScanner`'s
  abstract stack is an `ArrayDeque`, which rejects the raw-`null` "unknown ref" marker with a
  message-less NPE — every real-world handler containing a null literal / array op logged
  "ASM invocation scan failed … : null" and silently lost its invocation edges (diagnosed live
  from a field bundle's logs). Unknown refs are now a non-null `UNKNOWN` (`"?"`) sentinel;
  regression fixture `UnknownRefStackFixture` packs ACONST_NULL + ANEWARRAY + AALOAD.

Verified: full suite green on JDK 25 + JDBC ITs green under Docker (new Flyway IT ran, 1/1 passed).
**Released:** tag `v2.1.1` pushed 2026-07-03 (triggers release.yml + Maven Central publish).

**Field note (2026-07-03):** a field bundle's dev deploy hung before the start hook — its
dedicated consumer DB had been baselined at 1 by the old FlywayMigrator (non-empty schema), so
`evento_v2_*` tables were never created and no projector could reach head. Remedy for
already-poisoned DBs: run `V1__init_v2_consumer_state.sql` manually (or drop
`evento_v2_schema_history` and restart on ≥2.1.1). The 2.1.1 baseline fix only protects fresh DBs.

## Confinement check (2026-06-06) — released as v2.1.0

Branch `feat/confinement-check`, merged to `main` and released as **v2.1.0** (tag push triggers
release.yml + Maven Central publish). Motivated by peer review of the
RECQ paper (Prop. 2's confinement assumption was a *silent* failure mode): registration-time
detection of gateway calls invisible to static extraction.

- **`ConfinementScanner` (new, evento-bundle):** ASM sweep over every class in the scanned
  packages that is *not* a registered component; flags each `CommandGateway.send/sendAndWait` /
  `QueryGateway.query` call site there (class, method, line, kind). Reuses the gateway-call
  predicates now exposed package-private on `AsmInvocationScanner`.
- **`AsmInvocationScanner`:** `Result` gains `unresolved` (line → kind) — gateway calls whose
  payload is typed as the abstract `Command`/`Query` base (concrete type unresolvable) are now
  reported instead of silently dropped from the emit set.
- **Wiring (`EventoBundle.start`):** Reflections now configured with
  `SubTypes.filterResultsBy(c -> true)` so the whole package can be swept; component classes =
  union of the 7 component annotations. Violations → `logger.warn`; new builder flag
  **`strictConfinement`** (default `false`) → `IllegalStateException` at start-up (covers both
  gateway-leaks and unresolved sends; the latter via `HandlerMetadataBuilder.build(..., strict)`).
- **Tests:** `ConfinementScannerTest` (+ fixtures `confinement/LeakyHelperFixture`,
  `CleanHelperFixture`, `ComponentServiceFixture`, and `AbstractSendFixture`). Full
  `:evento-bundle:test`, `:evento-common:test`, `:evento-lab:test`, `:evento-lab-ms-it:test` green
  on JDK 25.

## OpenSSF Scorecard — round 3: Vulnerabilities + Binary-Artifacts + Fuzzing (2026-05-31, later still)

Branch `chore/dependency-upgrades` (not yet committed/pushed — changes in the working tree).
Tackled the three remaining *non-structural* Scorecard checks. Full detail in
[`SCORECARD-PLAN.md`](SCORECARD-PLAN.md).

- **Vulnerabilities (0 → expected high):** upgraded `evento-gui` across every breaking major —
  **Angular 18 → 21** (three `ng update` hops), ngx-markdown 18→21 + marked 12→16, ng-apexcharts
  1→2 + apexcharts 3→5, @ionic/angular 8.3→8.8.8 (+`skipLibCheck`), @ngx-translate 15→17 (loader
  API migration), Capacitor 6→8, mermaid 9→11, jsdom 20→29, jwt-decode 3→4, ESLint 8→9 (flat
  config). **`npm audit` 97 → 5**; the 5 left are unfixable (4 build-only dev-server transitives of
  the latest Angular toolchain + abandoned `mxgraph`). Production build green after every step.
  TypeScript held at `~5.9` and zone.js at `~0.15` (Angular 21 peers).
- **Binary-Artifacts (8 → expected higher):** deleted the unused `graphvizlib.wasm` (1 MB) + its 3
  dead deps (`graphviz-wasm`/`graphviz-builder`/`vizjs`, imported nowhere). Only `gradle-wrapper.jar`
  remains (required, validated in CI).
- **Fuzzing (0 → expected 10):** Jazzer `@FuzzTest` (`CborCodecFuzzTest` in `evento-transport-api`)
  on the CBOR `Codec.decode` — the most exposed parser (every wire byte hits it pre-auth). Per-PR CI
  runs it in fast regression mode; new weekly `fuzz.yml` runs `JAZZER_FUZZ=1` (libFuzzer mutation,
  120 s/target). Validated on JDK 25: 2.77 M execs/16 s, zero findings. Scorecard detects the
  `com.code_intelligence.jazzer` import.

**Not done (structural / manual):** Code-Review (needs a 2nd approver), Branch-Protection → higher
(enforce-admins/signed-commits conflicts with solo bypass), CII-Best-Practices badge (manual
registration). Backend Gradle deps untouched (Dependabot-managed).

## OpenSSF Scorecard — P0/P1/P2 round 2 (2026-05-31, later)

Second Scorecard pass off [`SCORECARD-PLAN.md`](SCORECARD-PLAN.md), landed via **PR #99**
(`chore/scorecard-p0-p1`, merged to `main` by admin-bypass) and re-scanned:
- **P0 Branch-Protection (-1 → 5):** `scorecard.yml` now passes `repo_token: ${{ secrets.SCORECARD_TOKEN }}`
  (classic PAT, maintainer-created) so Scorecard can read the live rules. Was `-1 internal error`
  (excluded); now a counted **High**-weight check.
- **P1 Pinned-Dependencies (8 → 9):** removed the last `pip install requests` — `publish.py`
  rewritten against stdlib (`urllib.request` + `base64` Basic auth); dropped `setup-python` +
  `pip install` from `maven-build-and-push-repository.yaml`; promote step runs `python3 publish.py`.
- **P2 CI-Tests (-1 → 10):** "1/1 merged PRs checked by CI" — credited by landing via a real PR.
- **Security:** `token.txt` (live PAT, was untracked + unignored) removed; `.gitignore` now blocks
  `token.txt`/`*.pat`/`*.token`.
- **Overall holds at 6.9** — now honest (includes Branch-Protection, previously excluded). The new
  counted High-weight 5 offsets the CI-Tests gain.

**Still capped by solo-maintainer structure:** Code-Review 0 ("0/28 approved changesets" — admin-bypass
merges have no approval); Vulnerabilities 0 (GUI majors, separate project); CII-Best-Practices 0 (manual
badge registration). **Skipped (marginal):** P1b Pinned-Deps 9→10 — the 2 leftover unpinned npm globals
are `npm install -g @ionic/cli@7.2.1` at `release.yml:61,141`; →10 needs ionic CLI as a lockfile devDep.

**Scheduled:** weekly remote routine `trig_01HZW2pKixvnjfSRDTT6mopp` (Tuesdays 12:00 UTC) re-checks the
Scorecard API and reports deltas vs the 6.9 baseline.

## OpenSSF Scorecard hardening (2026-05-31)

Worked the Scorecard report (was **5.4/10**). Shipped to `main` (admin-bypass pushes,
`0e73b72a` + `1468ddfa`):
- **Branch-Protection:** enabled on `main` (PR + 1 approval, dismiss-stale, required checks
  `build-and-test`/`jdbc-integration-tests`/`Analyze Java`, no force-push/delete, admins bypass).
- **Signed-Releases:** new consolidated `release.yml` (tag-triggered) — signed boot jar on a
  GitHub Release (cosign keyless sign-blob + SLSA provenance + checksums), multi-arch
  `linux/amd64,arm64` image to GHCR + Docker Hub (cosign-signed by digest, provenance, SBOM),
  and libraries to GitHub Packages. No new signing secrets (keyless).
- **Maven Central kept + made CI-capable:** the 5 publishable modules now declare both the
  ossrh (Central) and GitHubPackages repos; each workflow targets only its own repo. Creds +
  in-memory GPG key resolve from env (CI) or `gradle.properties` (local). `publish.py` is now
  env-aware and run by `maven-build-and-push-repository.yaml` to promote the `com.eventoframework`
  namespace after staging upload. Uploaded `MAVEN_CENTRAL_USERNAME/PASSWORD` + `SIGNING_KEY`
  (armored)/`SIGNING_PASSWORD` repo secrets via `gh`. jdbc (no Central target before) now included.
- **Pinned-Dependencies:** all 18 Action refs pinned to commit SHAs (`# vX` comments for
  Dependabot); Temurin bases pinned by digest; EOL/unpublished `openjdk:19` migrated to
  digest-pinned Temurin 25 JRE (+curl); `@ionic/cli@7.2.1` pinned.
- **Token-Permissions:** `codeql.yml` `security-events:write` scoped to the analyze job.
- Removed `docker-build-and-push-server.yml` (superseded by `release.yml`).

**Decisions deferred by maintainer:** License left at 9/10 (custom dual-license, classifier
won't match); **Vulnerabilities (~109)** deferred — `npm audit fix` (non-breaking) resolves 0;
all require breaking GUI majors (Angular 18→21, mermaid, Capacitor) = a separate project.
**Manual/TODO:** CII-Best-Practices badge (register at bestpractices.dev); Contributors check is
structural (solo project). Release workflows fire only on a `v*` tag — caveat: arm64 image builds
the jar under QEMU emulation (slow); optional future opt to build the arch-independent jar once.

## Enterprise-readiness assessment (2026-05-30)

`evento-parser` removed (`21d8cd78`) — the last consumer of the parser-based
`BundleDescription` model is gone now that ASM self-discovery + self-description parity
shipped. Module map in `ARCHITECTURE.md` §2 updated; §14 metrics claim corrected (not wired).

Ran a full enterprise-readiness assessment of the 6 production modules, then implemented
and **merged to `main`** the safe high-value tranche. Findings + phased plan + live tracker
in [`ENTERPRISE-PLAN.md`](ENTERPRISE-PLAN.md). Shipped (10 commits, `7c9ddf83`..`e91cd0dd`):
- **P0:** `PgDistributedLock` SQLi parameterized; `ChunkReassembler` bounded (byte/stream/TTL caps);
  `TracingAgent` marked honest no-op.
- **P1:** Micrometer wired (`BusMetricsBinder` + Prometheus); `BusHealthIndicator` + probes;
  secure-by-default (externalized JWT key, opt-in wire `TokenValidator`, REST `security.mode=open|token`,
  configurable CORS); opt-in polymorphic-deserialization allowlist (`PayloadTypeAllowlist`).
- **P2:** bus maintenance reaper (`ForwardingTable` + `reconnectBuffer`); Hikari/lock-lifecycle docs.
- **CI/build:** full server suite + JDBC IT job in CI; Netty reconciled to 4.1.124.
- Corrected over-claims: Maven/signing creds **not** committed (gitignored); logged `token=` is the
  internal connection UUID (not the secret); Dependabot/CodeQL/Scorecard already present.

**Not done (need a decision or a focused effort, see plan):** GUI token flow (separate plan);
event-store Flyway migration (10b — risky schema-bootstrap rework, needs a real Postgres to verify);
web/REST auth tests (12); `TransportServer.stop()` deadline (10 — SPI change); version catalog (13).
Item 7 hardening is opt-in only (default unchanged); enabling it per-deployment is an ops step.

## Deployment rip-out (2026-05-29)

The server no longer accepts bundle uploads, stores JARs, or carries any
deploy/scale config. Bundles are known to the server **only** via runtime
self-registration (`AutoDiscoveryService` over the wire). Removed:

- **Upload path:** `BundleController` POST `/` (`registerBundle`),
  `BundleService.register(...)` + `checkIsDAG()`, `ArtifactController` (deleted),
  `evento.file.upload-dir` (properties + docker-compose), `docker-spawn.py`,
  `evento_deploy_spawn_script`, `privileged: true`.
- **Per-bundle config:** env + VM options endpoints/service methods, `Bundle`
  fields `bucketType`/`artifactCoordinates`/`artifactOriginalName`/`environment`/
  `vmOptions`/`autorun`/`deployable`, `BucketType` enum (deleted), the two
  `core__bundle__{environment,vm_option}` tables, schema columns (with upgrade
  `ALTER`s). `Bundle` now tracks a single `instanceId` for leave-cleanup.
- **Parser:** `BundleDescription.autorun`/`deployable` + `EVENTO_BUNDLE_AUTORUN_PROPERTY`.
- **Auth:** `TokenRole.ROLE_DEPLOY` / `ROLE_PUBLISH` removed.
- **GUI:** spawn/kill buttons + service methods, env/VM-options editor, deployable
  settings rows, bundle-upload UI; dashboard metric `deployableBundleCount` →
  `bundleWithHandlersCount` (`countWithHandlers`).

Companion docs:
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — authoritative reference for architecture, design, classes, tests
- [`PLAN.md`](PLAN.md) — historical v2 rewrite plan (all PRs shipped)
- `STATUS.md` (this file) — session delta; update at end of every session

---

## Current state

| Item | State |
|---|---|
| Branch | **`main`** (active development). `next` merged. |
| Version | **`2.3.0`** (latest `chore(release)` on `main`; 2.1.1 was the last entry detailed here). |
| v2 rewrite | **Complete** — all 3 PRs (transport / server / bundle) shipped |
| Test count | ~245 on JDK 25 (transport, server, common, bundle, lab, lab-ms); 50+ JDBC ITs gated on Docker |
| Known issues | None open post Fix A–D |

## What shipped (v2 rewrite summary)

- **Transport:** `evento-transport-api` (sealed `Message` SPI, `InMemoryTransport`) +
  `evento-transport-netty` (Netty pipeline, chunking, TLS, heartbeat, zero-copy forwarding)
- **Server bus:** `BusLifecycle` orchestrator replacing `MessageBus.java`; composable
  `ConnectionRegistry`, `ClusterRegistry`, `CorrelationStore`, `ForwardingTable`,
  `HandshakeHandler`, `TokenValidator`, `MessageRouter`, `BusEventBus`
- **Bundle client:** `BundleClient` + `ConnectionSupervisor`, `BundleCorrelationTracker`,
  `ProcessedRequestCache`, `HandlerRegistry`, `InboundDispatcher`, `EventoServerAdapter`
- **Consumer engines:** `ProjectorEngine`, `SagaEngine`, `ObserverEngine`, `EngineSupervisor`
  composed on five focused SPIs + JDBC impls (Postgres + MySQL, Flyway migrations)
- **Command broker:** `CommandBrokerHandler` restores aggregate + service command interception
  on top of `BusLifecycle`
- **v1 deletion:** `MessageBus.java`, v1 bundle transport (1 634 LOC), v1 consumer abstract class all gone
- **Post-RC1 fixes:** Fix A (superseded-session race), Fix B (FK violation), Fix C (DEGRADED
  drops), Fix D (QoS reconnect delivery for in-flight responses)

## v2.0.0 GA — shipped (2026-05-30)

`2.0.0` was tagged and pushed, triggering the **Publish to Maven Central** and
**Docker Build & Push — Server** workflows. The pre-GA staging soak (deploy to
staging, soak 1–2 weeks with early adopters) was **skipped by maintainer decision**.
Post-release: confirm both workflows succeeded (Actions tab), verify artifacts on
Maven Central (`com.eventoframework:evento-bundle:2.0.0`) and the
`eventoframework/evento-server:2.0.0` Docker image, then watch for adopter findings.

## Next feature planning

### ASM-based self-discovery of invocations (2026-05-26) ✅

**Goal:** eliminate the `evento-cli` static-analysis step for building the dependency graph.
Bundles now scan their own bytecode at startup and publish invocation edges as part of `BundleDiscoveryInfo`.

**Changes shipped:**

| Module | Change |
|--------|--------|
| `evento-common` | `RegisteredHandler` +`invokedCommands: List<String>` + `invokedQueries: List<String>` |
| `evento-bundle` | `AsmInvocationScanner` — single-pass class scan + BFS from handler through intra-class call graph |
| `evento-bundle` | `HandlerMetadataBuilder` — calls scanner for every handler (aggregate, service, projector, observer, saga, projection, invoker) |
| `evento-bundle` | `InvokerManager` — exposes `handlerMethods` map so metadata builder can access Method objects for invokers |
| `evento-bundle/build.gradle` | `org.ow2.asm:asm:9.10.1` (JDK 25 / class file v69 compatible) |

**Algorithm guarantees:**
- Detects direct `gateway.send(new Cmd())` calls
- Detects transitive calls through any depth of private/package helper methods (BFS, cycle-safe)
- Detects lambda bodies via `INVOKEDYNAMIC` → `LambdaMetafactory` bootstrap handle tracing
- Isolated per handler — scanning `handlerA` cannot bleed into `handlerB`

**Tests:** 9 tests in `AsmInvocationScannerTest` covering direct, one-hop, three-hop, lambda, ServiceCommand, Query, isolation, and empty cases.

**Server-side complete (2026-05-27):** `AutoDiscoveryService.onNodeJoin` now:
- Calls `buildInvocations(registeredHandler, bundle.getId())` when creating a new ephemeral handler
- Calls the same helper when an existing handler has empty invocations (backfill on re-registration)
- `buildInvocations` iterates `invokedCommands` and `invokedQueries`, resolves or creates ephemeral
  `Payload` entities (type `Command`/`Query`), and returns `Map<Integer, Payload>` for `Handler.invocations`
- `resolveOrCreatePayload` reuses existing payloads or creates ephemeral ones with `jsonSchema="null"`,
  `validJsonSchema=false`, same pattern as handler/component creation

---

### Full self-description parity with CLI — PLAN (not yet started)

**Goal:** Make bundle startup registration carry everything that `BundleDescription` (CLI) carries,
so the `evento-cli` static-analysis + publish step is entirely optional.
Excluded intentionally: JAR upload, `autorun`, `deployable`, min/max instances, `artifactCoordinates`.

#### Source-link URL formula

The server builds a clickable source link from three pieces:
```
{bundle.repositoryUrl}/{component.path}#{bundle.linePrefix}{line}
```
Example (Bitbucket — `linePrefix = "lines-"`):
```
https://bitbucket.org/myorg/repo/src/main/order-bundle/com/example/OrderAggregate.java#lines-42
```
Example (GitHub/GitLab — `linePrefix = "L"`):
```
https://github.com/myorg/repo/blob/main/order-bundle/com/example/OrderAggregate.java#L42
```
- `repositoryUrl` — repo browser base URL for this bundle+branch (deployment-time config, not in bytecode)
- `linePrefix` — anchor format (`lines-` for Bitbucket, `L` for GitHub/GitLab)
- `component.path` — relative source path extracted by ASM (e.g. `com/example/OrderAggregate.java`)
- `line` — component, handler, payload, or invocation line depending on what is being linked

#### Gap being closed

| Level | Currently missing from wire protocol |
|-------|--------------------------------------|
| Bundle | `description`, `detail`, `repositoryUrl`, `linePrefix` |
| Component | `description`, `detail`, `path` (source file relative path), `line` (class declaration line) |
| Handler | `line` (method declaration line) |
| Payload | `description`, `detail`, `path`, `line` (currently only `schema` + `domain` in `String[]`) |
| Invocations | line numbers (currently `List<String>` names only; server model uses `Map<Integer, Payload>`) |

#### Description/Detail standard

Javadoc is stripped from class files at compile time. The solution is a lightweight annotation in `evento-common`:
```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface EventoDescription {
    String value() default "";   // short one-liner shown in dashboards
    String detail() default "";  // markdown long-form
}
```
Usage:
```java
@EventoDescription(value = "Manages order lifecycle", detail = "Handles all commands that mutate order state...")
public class OrderAggregate extends Aggregate { ... }
```
Fallback when absent: description = class simple name; detail = "". Zero effort for developers who skip it.

#### Repository URL and line prefix — application.properties

`repositoryUrl` and `linePrefix` come from deployment configuration, not bytecode.
Added as optional fields on `EventoBundle.Builder`:
```java
EventoBundle.Builder.builder()
    .bundleId("order-service")
    .repositoryUrl("https://bitbucket.org/myorg/repo/src/main/order-service")
    .linePrefix("lines-")
    ...
```
In Spring Boot applications, inject via:
```java
@Value("${evento.bundle.repository-url:}") String repositoryUrl,
@Value("${evento.bundle.line-prefix:L}")   String linePrefix
```
Standard property keys (document in ARCHITECTURE.md):
- `evento.bundle.repository-url` — no default (empty = source links disabled)
- `evento.bundle.line-prefix` — default `L`

#### Implementation steps (in order)

**Step 1 — `evento-common`** ✅
- [x] `@EventoDescription` annotation in `com.evento.common.modeling.annotations`

**Step 2 — `evento-transport-api`** ✅
- [x] `PayloadDiscoveryInfo` record (schema, domain, description, detail, path, line)
- [x] `BundleDiscoveryInfo` uses `Map<String, PayloadDiscoveryInfo>`; adds description, detail, repositoryUrl, linePrefix

**Step 3 — `evento-common`: enrich `RegisteredHandler`** ✅
- [x] componentDescription, componentDetail, componentPath, componentLine
- [x] handlerLine
- [x] invokedCommands/invokedQueries: `Map<Integer, String>` (source line → simple name)

**Step 4 — `evento-bundle`: ASM extraction + config wiring** ✅
- [x] `AsmClassMetadataScanner`: sourcePath (visitSource + package), declarationLine (min <init> line), description/detail (@EventoDescription via reflection)
- [x] `AsmInvocationScanner`: tracks currentLine via visitLineNumber; returns Map<Integer,String>; handlerLine in Result
- [x] `HandlerMetadataBuilder`: applyComponentMetadata + applyInvocations for all handler types; PayloadDiscoveryInfo with schema/domain/description/detail/path/line
- [x] `EventoBundle.Builder`: repositoryUrl, linePrefix, description, detail fields
- [x] `BundleClientConfig`: all new fields, payloadInfo type changed
- [x] `ConnectionSupervisor`: passes all new fields to BundleDiscoveryInfo

**Step 5 — `evento-server`** ✅
- [x] Bundle JPA entity: repositoryUrl column added
- [x] AutoDiscoveryService: bundle description/detail/repositoryUrl/linePrefix, component path/line/description/detail, handler line, payload description/detail/path/line, invocations keyed by real source line

**Step 6 — Tests (unit)** ✅
- [x] AsmInvocationScannerTest updated for Map<Integer,String>; handlerLine > 0; invocation keys are source lines
- [x] Server IT tests (BusLifecycleIT, BusLifecycleFacadeNettyIT) updated for new BundleDiscoveryInfo constructor and PayloadDiscoveryInfo
- [x] AsmClassMetadataScannerTest — 6 tests: annotated/plain description, sourcePath, declarationLine
- [x] `AsmClassMetadataScanner`: null-classloader guard (bootstrap-loaded classes → system classloader fallback)

**Step 7 — End-to-end verification** ✅
- [x] Started evento-server locally (Docker Postgres 16 ephemeral)
- [x] Started lab-ms-command with `repositoryUrl=https://github.com/...` + `linePrefix=L`
- [x] `GET /api/bundle/lab-ms-command` → repositoryUrl ✅ linePrefix ✅ handler path/line ✅
- [x] `GET /api/catalog/component/OrderAggregate` → path=`com/evento/lab/ms/command/aggregate/OrderAggregate.java`, line=14, repositoryUrl correct
- [x] Source link generated: `{repositoryUrl}/{path}#L{line}` resolves to correct GitHub URL
- [x] `GET /api/catalog/payload/CreateOrderCommand` → path=`com/evento/lab/ms/api/command/CreateOrderCommand.java`, line=12
- [x] `GET /api/flows/bundle/lab-ms-command` → graph nodes include source location
- [x] `schema.sql` fix: added `repository_url text null` column + `ALTER TABLE … ADD COLUMN IF NOT EXISTS` for live upgrades
- [x] `BundleDto` and `ComponentDTO` updated to expose `repositoryUrl` in REST responses
- [x] No regression in `evento-lab-ms-it` IT suite; `evento-lab` InMemoryConsumerIT flaky test is pre-existing

#### Key files to read at session start
- `evento-transport-api/.../BundleDiscoveryInfo.java`
- `evento-common/.../RegisteredHandler.java`
- `evento-bundle/.../AsmInvocationScanner.java`
- `evento-bundle/.../HandlerMetadataBuilder.java`
- `evento-bundle/.../client/BundleClientConfig.java` — add `repositoryUrl`/`linePrefix`
- `evento-bundle/.../client/connection/ConnectionSupervisor.java` line 294 — constructs `BundleDiscoveryInfo`
- `evento-bundle/.../EventoBundle.java` Builder — add `repositoryUrl`/`linePrefix` setters
- `evento-server/.../AutoDiscoveryService.java`
- `evento-server/domain/model/core/Bundle.java` — add `repositoryUrl` field

---

### evento-cli removal (2026-05-29) ✅

With ASM self-discovery + full self-description parity shipped (above), the `evento-cli`
static-analysis + publish step is fully redundant. Module deleted.

- Deleted `evento-cli/` (`PublishBundle`, `UpdateVersion`, `Test`) + `settings.gradle` include
- Deleted `.github/workflows/cli-release.yaml`; removed CLI from README, `ARCHITECTURE.md`, `bug_report.yml`
- ~~**Kept** `evento-parser`~~ — **superseded:** `evento-parser` was removed on 2026-05-30
  (`21d8cd78`). See the 2026-05-30 entry below.
- **Dropped, not migrated:** deploy-by-upload *client* (server endpoint stays) and the
  `UpdateVersion` version-bump helper (now a manual edit of `evento.bundle.version`); both were
  intentionally out of scope for auto-discovery parity
- `./gradlew projects` configures cleanly without the module

## How to run locally

```bash
# Build (skip tests)
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew clean build -x test

# Full test suite
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
  :evento-transport-api:test \
  :evento-transport-netty:test \
  :evento-server:test --tests 'com.evento.server.bus.*' \
  :evento-common:test \
  :evento-bundle:test \
  :evento-consumer-state-store:evento-consumer-state-store-jdbc:test \
  :evento-lab:test \
  :evento-lab-microservices:evento-lab-ms-it:test

# JDBC ITs (requires Docker)
EVENTO_RUN_JDBC_IT=true JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-consumer-state-store:evento-consumer-state-store-jdbc:test
```

## Notes for Claude on resume

Read `ARCHITECTURE.md` first — it has the full module map, wire protocol, class responsibilities,
design decisions, and test infrastructure. This `STATUS.md` is the session delta only.
Commit pattern: `feat(module): …` / `fix(module): …` / `chore(build): …` with rich body.
