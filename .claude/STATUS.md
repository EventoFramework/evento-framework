# Evento Framework — status snapshot

Last updated: 2026-08-05. Branch `next` merged to `main`; v2.0 rewrite complete.
`evento-cli` **and** `evento-parser` modules deleted; deployment/autoscaling surface removed.

## CI warning sweep — logs now clean (2026-08-05, later)

Went through the latest green CI logs hunting warnings; three commits (`f91fea1d` gui,
`bb8361a1` deprecations, `7d280bc8` gradle) cleared every solvable one. Verified against the
fresh post-push CI run: zero NG8113, zero Sass deprecation, zero javac deprecated-API notes,
zero "Deprecated Gradle features", zero direct-dep npm deprecations.

- **gui**: NG8113 unused imports trimmed (3 components). **The Sass fix mattered more than it
  looked**: `global.scss`'s deprecated `@import "theme/variables"` was a *double emission* of
  the brand tokens, and its position was the only thing letting the brand beat Ionic core.css's
  default `:root` palette (styles-array entry loads *before* ionic css, so it always lost).
  Ionic css + tokens now load via angular.json's styles array in explicit cascade order;
  verified in the built bundle (single `#002121` primary, after Ionic's `#0054e9`).
  `platform-browser-dynamic` removed (main.ts → `platformBrowser`, dev `aot:true`, test.ts →
  `platformBrowserTesting` + explicit `@angular/compiler`); `@angular/animations` (no imports)
  and `@types/cytoscape` (stub) dropped. Runtime-validated headless (login renders, i18n ok).
- **Netty 4.2 API**: `NioEventLoopGroup` → `MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())`,
  watermark options → single `WRITE_BUFFER_WATER_MARK`, in both transports.
  `BundleClientIT`'s TLS test → `netty-pkitesting` `CertificateBuilder`; needs
  `setIsCertificateAuthority(true)` or the builder throws and **the test's no-provider
  Assumption silently converts the failure into a skip** — it sat skipped=1 in the "green" run
  until the result XML was read. 8/8 run now. Watch that Assumption if the test "passes"
  suspiciously fast.
- `PerformanceStoreService`: deprecated `JdbcTemplate.query(sql, Object[], mapper)` → varargs.
- **Gradle 10 prep**: all 27 Groovy space-assignments (`group 'x'` etc.) converted;
  `--warning-mode all` clean.

**Known-unsolvable warnings left in CI logs (all upstream):** lombok + Jazzer `sun.misc.Unsafe`
notes; npm transitive deprecations (rimraf/glob/inflight — they come with the webpack-based
`build-angular:browser` builder); the 3 hono/MCP moderates inside `@angular/cli`; npm
`allow-scripts` policy notes. **The one worthwhile follow-up: migrate `angular.json` from the
deprecated `build-angular:browser` (webpack) to `@angular/build:application` (esbuild/vite)** —
Angular 22.1 already ships the toolchain (rolldown/oxc/lightningcss landed in the lockfile);
that migration clears the builder deprecation AND the transitive npm warnings, but it changes
bundling behaviour (styles order, allowedCommonJsDependencies, output layout feeding
`evento-server/src/main/resources/public/`) so it deserves its own session with runtime checks.

## PgDistributedLock release() race fixed + Dependabot queue emptied (2026-08-05, earlier)

**Issue #216 closed** (`d15325eb`, `fix(common)`, on `main` unreleased): under concurrent
publishing through one key (`EventStore` funnels all service commands through `"es-lock"`),
`release()` intermittently threw `IllegalMonitorStateException: No lock held for key` from a
thread that legitimately held the lock — thrown from `lockedArea`'s `finally`, it also replaced
the critical section's real outcome (successful `publishEvent` reported as a 500) and skipped
`pg_advisory_unlock`, leaking the session-level advisory lock. Root cause: `release()`
decremented the wrapper's queue count and unmapped it in **two separate steps**, so a concurrent
`acquire()`'s `compute` could join a wrapper about to be unmapped. As old as the class; 2.4.x's
`BusBusinessExecutor` just pushes enough concurrency through it to hit the window in practice.

- New `exitQueue(key)`: decrement + removal inside one `locks.compute` (same bin lock as
  `acquire`'s `compute`). A holder's own +1 keeps the count above zero between acquire and
  release, so its wrapper can never be unmapped underneath it. Also applied to the rollback
  paths of `acquire()`/`tryAcquire()`, which had the same non-atomic pattern.
- `lockedArea`/`tryLockedArea` release via `releaseWithoutMasking`: a release failure is
  suppressed onto an in-flight exception or logged (`event=lock_release_failed`) after a
  successful critical section — cleanup can no longer turn applied work into a reported
  failure (which invites a retry of work that already happened).
- With the race fixed, `release()`'s null-wrapper branch genuinely means
  release-without-acquire, and skipping the PG unlock there is correct.
- `PgDistributedLockTest` (evento-common, embedded mode — null DataSource, no PG): the
  8-thread × 5000-iteration single-key hammer was **verified failing on the pre-fix code**,
  and pins mutual exclusion via an unsynchronized counter. Distributed half stays covered by
  `PgDistributedLockIT` (jdbc module). **Ships in the next release** — the reporter is on
  2.4.3, which still has the race.

**Dependabot sweep: 15 PRs + the issue → both queues empty.** Merged green directly
(squash + `--admin`, branches deleted): docker/login-action 4.6.0 (#201), docker/metadata-action
6.2.0 (#215), classgraph 4.8.186 (#202), mysql-connector-j 26.7.0 (#203 — Oracle moved the
connector to server-aligned calendar versioning; 9.7.0 → 26.7.0 is one release, all bugfixes;
`jdbc-integration-tests` green), hibernate-validator 9.1.3 (#204).

**The Angular-family lesson recurred exactly as documented** (third time): #205/#207/#208/#210/
#211/#212 each failed `gui-build` with ERESOLVE (`peer @angular/core@"22.1.0"` — exact-version
peers). Landed as one commit via `ng update @angular/core@22.1.0 @angular/cli` on
`chore/gui-deps-2026-08` → **#217** (merged, all 5 checks green), folding in the four green npm
PRs too (#206 @ionic/angular → 8.8.17, #209 jsdom 30, #213 language-service, #214 ionicons 8.1.0)
since each npm PR invalidates the others' lockfile. All 10 closed with pointers to #217.
Note for next sweep: plain `npm install`/`npm update` could NOT untangle the exact-version peer
web against the existing lock even with all package.json entries pre-bumped — go straight to
`ng update`, it needs a clean tree and handles the family + lockfile coherently.

- **Angular 22.1 swapped its build pipeline to rolldown/oxc-parser/lightningcss** (vite-based).
  The lockfile's os-scoped optional-dep diff is large but additive with full platform matrices
  (linux-x64 gnu+musl present — the Windows-regeneration check from the 2026-07-25 sweep passes).
  `@angular/cli` now ships an MCP server: its `@modelcontextprotocol/sdk` → `hono` chain carries
  **3 moderate audit findings, dev-time only, upstream-blocked** (the only "fix" npm offers is
  downgrading the CLI). `npm audit fix` cleared brace-expansion + fast-uri (both high) en route.
- Validated with prod `ng build` + `ng lint` locally (Node 24 / npm 11); `gui-build` green in CI.

## Startup gate is now opt-in: `@Projector(waitForHeadReached)` (2026-07-28, latest)

**Behaviour change:** the bundle no longer waits for projectors to reach the event-stream
head before enabling. Default is `waitForHeadReached = false` on `@Projector` — the bundle
enables immediately after registration, sagas/observers start right away, and projectors
align in the background (the existing `Projector head reached: …` log marks catch-up).
Annotating `@Projector(waitForHeadReached = true)` restores the old gate for that projector:
the bundle stays disabled until every gating projector (per context) is aligned, including
the async-handler drain before the gate releases.

- `Projector` annotation: new `waitForHeadReached()` attribute (default `false`).
- `ProjectorEngine`: new `waitForHeadReached` constructor arg; only gating engines touch the
  alignment counter (older constructor overloads default to `true`, preserving their contract).
- `EventoBundle.startProjectorEnginesV2` now returns the gating-consumer count and releases
  the enable gate immediately when it is zero.
- `AsyncLabProjector` opted in (`waitForHeadReached = true`) so
  `bundleIsNotEnabledUntilAsyncHandlersHaveFinished` keeps asserting the drain-before-enable.
- New `evento-lab` IT `StartupGateIT` (package `com.evento.lab.gate`): latch-blocked
  projectors prove both timings (default enables while behind head; opted-in holds until aligned).

**Discovery bug fixed en route:** `EventoBundle` scanned with `Reflections.forPackages(...)`
only, which selects classpath *roots* containing the package — every class sharing those roots
was scanned too, so two bundles in one jar/source-set could never be isolated (the new gate test
bundles picked up `AsyncLabProjector` and failed executor validation; the async ITs symmetrically
picked up the gate projectors). Added `filterInputsBy(FilterBuilder.includePackage(basePackage))`
so `setBasePackage` means what it documents. Note the behaviour change: components in sibling
packages outside the base package are no longer discovered (e.g. `ConnectivityIT`'s two-bundle
scenario now registers genuinely distinct handler sets).

## Dependabot: 5 Actions bumps landed, queue empty again (2026-07-27, latest)

All five open PRs merged green (5/5 checks each), squash + `--admin`, branches deleted:
`actions/upload-artifact` 6.0.0→7.0.1 (#199, `e7f9d0fa`), `gradle/actions/wrapper-validation`
5.0.2→6.2.0 (#198, `01b7618f`), `docker/login-action` 4.2.0→4.5.1 (#197, `bad0f319`),
`docker/build-push-action` 7.2.0→7.3.0 (#200, `9538fde4`), `softprops/action-gh-release`
3.0.1→3.0.2 (#196, `fd819392`).

**No repeat of the two failure modes from the last sweep.** Base commits were all `93d66c4b`,
a real ancestor of `main` — not orphaned history. And each PR bumped *every* occurrence of its
action (login-action ×2, build-push-action ×2, upload-artifact ×3 across `ci.yml` + `fuzz.yml`),
so nothing was left half-bumped. Both are worth re-checking on every sweep; they are the two
things that have gone wrong before.

**The two majors were safe for our usage.** `upload-artifact` v7 breaks on an internal ESM port
plus a new opt-in `archive` param whose default preserves v6 behaviour — we only pass
`name`/`path`/`retention-days`. `wrapper-validation` v6's release notes are almost entirely
about `setup-gradle` caching; we invoke it with no inputs at all.

**⚠️ Three of these were not actually validated by PR CI.** #196, #197 and #200 touch only
`release.yml`, which runs on tag push — PR CI never executes it, so green meant "nothing else
broke", not "the bump works". Same shape as the older *frontend never built in PR CI* lesson.
They get their first real exercise on the next `v*` tag; **`docker/login-action` and
`docker/build-push-action` are the two to suspect if that release fails.**

Merging these three also re-touched `release.yml` right beside the cache change below (#200
lands adjacent to the removed `cache-to`). Verified on `main` afterwards: `cache-to` still
absent, both `cache-from` lines intact, no `cache:` keys in either tag-triggered workflow.

## Actions cache quota exhausted — tag-scoped caches were write-only (2026-07-27)

GitHub reported the repo out of Actions cache space: **11.41 GB across 127 caches**, over the
10 GB per-repo ceiling, at which point GitHub LRU-evicts — so `main`'s genuinely useful caches
were being thrown away to make room for garbage.

**Root cause: an Actions cache is scoped to the ref that wrote it.** `release.yml` and
`maven-build-and-push-repository.yaml` run only on a fresh `v*` tag, and a tag ref never
recurs — so every cache those workflows saved was unreadable by the next release, for ever.
The evidence was unambiguous: **100% of the 3.34 GB `buildkit-blob` cache sat on tag refs**
(`refs/heads/refs/tags/v2.3.1|v2.3.2|v2.4.0`, ~1.1 GB each), **zero on `main` or any PR**.
Total dead weight 3.95 GB over three releases, ~1.3 GB per release, growing monotonically.

**Cleanup.** Deleted 109 caches (11.41 GB → **2.65 GB**, 127 → 18), all provably unreachable:
82 on the three release tag refs, 27 on PR merge refs (verified all 17 PRs closed first). Kept
all 18 `main` caches — those are the only ones CI reads back. Note the `cache/usage` endpoint
is **eventually consistent**: right after the deletes it still reported 11.38 GB while a fresh
`actions/caches` list showed 18. Verify with the list endpoint, not `usage`.

**Fix (this session), in four jobs across two workflows:**
- `release.yml` docker job: dropped `cache-to: type=gha,mode=max`. A `mode=max` export of the
  two-platform (amd64+arm64) build is what produced the 1.1 GB/release. `cache-from` is kept —
  it costs nothing and still lets the `-cf` variant reuse layers **within the same run**.
- Dropped `cache: gradle` / `cache: npm` from the tag-triggered `boot-jar`, `docker` and
  `maven-packages` setup steps, plus the fourth instance in
  `maven-build-and-push-repository.yaml` (same defect, easy to miss — it is a separate file).

**Zero build-time cost**, and that is the whole point: these caches were never being *read*,
so removing the writes cannot cause a miss that was not already happening.

**`ci.yml`, `codeql.yml` and `fuzz.yml` keep their caching, deliberately** — they run on
branches and PRs, where the writing ref recurs and the cache is genuinely reused.

**The rule worth remembering: never enable dependency caching in a tag-triggered workflow.**
It is pure quota burn, it is invisible (no warning, no failed step), and it degrades the
caches that *do* work by pushing the repo into LRU eviction. Artifacts were never the problem
here — only 0.10 GB live across 206; the 455 expired ones (2.11 GB) are already uncounted.

## OOM zombie-broker hardening + discovery FK fix (2026-07-27)

Production incident (market deployment, 2026-07-27 ~03:58–07:00 UTC): the broker OOMed
while serving `EventFetchRequest`s, the error killed Netty **worker event-loop threads**,
every `catch (Throwable)` swallowed it, and the process survived as a zombie — the boss
loop kept accepting TCP and force-closing each channel ("registration task was not accepted
by an event loop", 304 times at the client's ~30s retry cadence) for three hours. Because
the JVM never exited, `restart: on-failure` never fired; recovery required a manual
restart. This is the same failure family as the 2026-07-25 congestion-collapse entry below:
work is never cancelled when its caller's deadline passes, and retries make the overload
self-sustaining.

**Changes (all in `main`, unreleased):**
- `FatalErrors` (evento-common, `com.evento.common.utils`) — cause-chain walk for
  `VirtualMachineError`; `escalateIfFatal` halts with exit code 3 (same as
  `ExitOnOutOfMemoryError`), gated by `-Devento.fatal.halt` (default on). Called FIRST in
  the catch-alls in `BusLifecycle` (maintenance + local-handler), `BusEventBus.publish`,
  `ConnectionRegistry` (supersede-close + closeAll).
- `EventoServerApplication.main` installs a default uncaught-exception handler delegating
  to `FatalErrors` — this is the hook that would have fired for the event-loop deaths.
- All three evento-server Dockerfiles: `ENTRYPOINT` now passes
  `-XX:+ExitOnOutOfMemoryError`.
- `EventStoreRequestHandlerBridge`: staleness gate `evento.es.fetch.max.age.ms` (default
  30000, ≤0 disables, unstamped requests exempt) checked *after* permit acquisition —
  requests that aged out in the semaphore queue are the ones whose callers already retried.
  Logic pinned in `EventStoreRequestHandlerBridgeTest.isExpired` tests.
- `Consumer.component`: dropped `cascade = ALL` (a consumer delete cascaded a REMOVE to the
  shared `core__component` row → FK violation `core__consumer_component_component_name_fkey`
  on every reconnect). Component lifecycle already belongs to discovery/`BundleService`.

**Deliberately not done:** no cap/streaming of fetch-response size (the per-batch ~4-5×
copy amplification stands, bounded only by the permit count); `DEGRADED.canSend()` still
`true`, so writes to an unwritable channel still queue unbounded in Netty's outbound
buffer (`BackpressureHandler` remains a state mirror). Both are candidates for the next
capacity pass.

## Request-path capacity: growth-first pool, typed timeouts, saturation meters (2026-07-25)

Came out of a production incident on a bundle doing a bulk catalogue import: 20 concurrent
writers against an 8-core server fell from 16 writes/s to **0.07 writes/s** with ~250
`local_handler_threw … TimeoutException` per hour. CPU sat under 1%, both databases were
idle, `int_lock` was empty, no query was blocked. Removing the load and sending a single
command returned in 0.1s — nothing was wedged. Re-running the same job at **8** concurrent
writers sustained 25 writes/s with zero timeouts. Less concurrency, more throughput.

**Root cause.** `busBusinessExecutor` was `new ThreadPoolExecutor(core=cores×2,
max=cores×8, new ArrayBlockingQueue<>(1024), CallerRunsPolicy)`. A `ThreadPoolExecutor`
starts a thread beyond `core` only when the queue is **full**, so with a 1024-deep queue the
pool never left 16 threads — `max=64` was unreachable under precisely the load it was
configured for, and `CallerRunsPolicy` never engaged either. Because every request carries a
30s client deadline and *nothing cancels work when it expires*, the queue filled with
requests whose callers had gone: the server stayed fully busy producing responses nobody
read. That is congestion collapse, and retries make it self-sustaining.

**Changes.**
- `BusBusinessExecutor` + its `GrowthFirstQueue` (Tomcat's approach): refuse the offer while
  the pool can still grow, so it reaches `max` before queueing; `submittedCount` is tracked
  in `execute`/`afterExecute` to avoid `getActiveCount()`'s pool lock on every submission.
  Queue default `1024 → 256` — deeper than `deadline ÷ service time` is dead work.
- `RequestTimeoutException` / `TooManyPendingRequestsException` in `com.evento.transport`.
  `EventoServerAdapter.request` reconstructs them from `ResponseError.exceptionClassName()`
  instead of flattening every failure to `IllegalStateException`. This mattered in the
  incident: ~20 commands reported as failures had in fact been **fully applied** — the
  timeout was the caller giving up, not the handler refusing. Callers can now report 504
  rather than 500, and avoid double-applying on retry.
- `BundleClient.request` enforces `BundleClientConfig.maxInFlightRequests` (default 2048),
  failing fast rather than queueing work that will expire unsent.
- Meters `evento.server.bus.executor.{pool.size,max,active,queue.depth,saturated}`;
  `saturated` is the alerting signal. Expiries moved `INFO → WARN`, and the bundle-side line
  gained `byType={...}` — the old line reported a count and nothing actionable.

**Not changed, deliberately.** `evento.es.fetch.concurrency` stays at 4. It is very likely
the binding constraint on a consumer-heavy cluster (a fair semaphore, so no number of bus
threads raises it), but it guards heap against concurrent `EventFetchRequest` result sets
and the OOM it was added for is real. Raise it against measured headroom, not on principle.
Documented as a capacity knob instead.

Docs: `CHANGELOG.md` (Unreleased), `README.md`, ARCHITECTURE §12, and a new public page
*Evento Server → SetUp Evento Server → Throughput and Capacity* in `evento-doc`.

## Async consumers — complete, Phases 1–4 shipped (2026-07-25)

New feature: `@EventHandler(executor = "name")` dispatches an event to a named, bounded
`ConsumerExecutor` registered on the bundle builder
(`.addConsumerExecutor(ConsumerExecutors.virtual("read-model", 64))`), so idempotent /
overwrite handlers consume **in parallel** instead of one event at a time. Default
(`executor = ""`) is unchanged inline behaviour — no existing test needed modifying.
Full plan, deviations and negative-control results in
[`ASYNC-CONSUMERS-PLAN.md`](ASYNC-CONSUMERS-PLAN.md) §12.

**The load-bearing idea: the checkpoint advances when a task *starts*, not when it
finishes.** `ConsumerExecutor.submit` returns a future that completes at task start, and
the consume loop awaits it before committing. That single choice supplies the backpressure
(the loop can never run more than `capacity` events ahead of completion, so no unbounded
prefix of the event store is ever enqueued) and bounds the crash-loss window to the set of
*running* tasks rather than a queue depth. Submit is time-boxed (5 s default): on
saturation the cycle ends at the last started event and releases the consumer lock —
important because the JDBC `ConsumerLock` pins a pooled connection for the whole cycle.

**Three consequences that will bite operators, all documented in the annotation javadoc:**

- **At-most-once for the in-flight window.** Idempotency protects against duplicates, not
  loss. A graceful stop drains (supervisor → engine drain → `BoundedConsumerExecutor.shutdown`
  awaits quiescence); a `kill -9` loses whatever was running.
- **Connection-pool sizing gains a second term.** On top of ARCHITECTURE §12's one
  connection per active consumer (`LockHandle` pins it), every concurrently-running
  transactional handler holds one too. Size for
  `consumers + Σ(capacity of transactional executors) + headroom`. Under-sizing surfaces as
  acquisition timeouts, which `isTransient` flags transient and — see next point — dead-letter
  immediately.
- **`retry = -1` (the annotation default) is coerced to `0` under an executor**, because
  retry-forever inside a task pins a concurrency permit and starves the executor. So an
  async handler with default settings dead-letters on first failure, transient ones
  included. Set an explicit `retry` for anything touching a remote dependency. Phase 2's
  degraded-flag backoff is what will stop an outage burning the stream into the DLQ.

**Interceptor-managed transactions survive unchanged**, and the reason is now a documented
invariant on `MessageHandlerInterceptor`: for one event, `before → handler → after/onException`
always run on **one thread**, and under async that is the executor's task thread, never the
fetch loop's. `ThreadLocal`-backed transaction managers therefore behave identically inline
and in parallel; each retry attempt still gets its own before/after pair, so its own
transaction. Two new obligations on implementors (also in the javadoc): the instance is now
entered concurrently, so per-invocation state must be in `ThreadLocal`s not fields; and
unbind in a `finally`, because the framework's DLQ write happens on the same thread right
after `onException` returns. `AsyncConsumerIT.interceptorAndHandlerShareOneThreadSoThreadLocalTransactionsWork`
pins all of it.

**Two bugs this fixed/avoided along the way:**

- **The bundle-enable race.** `ProjectorEngine` signals head-reached when
  `consumedEventCount < fetchSize`, and that is what triggers `eventoServer.enable()`. Under
  async, "consumed" means "started" — so without a drain the bundle would be enabled, and
  start serving queries, against a read model still being written. `drainAsyncHandlers()`
  now runs before the alignment counter is decremented. This is the highest-value
  regression test in the set; note it only becomes sensitive with a handler delay well
  above the enable round-trip (1.5 s in the IT — at 200 ms it passed even with the drain
  removed).
- **Observers were already doing this, badly.** They dispatched to an unbounded
  `newVirtualThreadPerTaskExecutor` and already checkpointed on submit, so an observer
  catching up from a cold checkpoint submitted its whole backlog as fast as it could fetch
  it. `ConsumerEngineConfig.inMemory` now wires a bounded executor
  (`DEFAULT_OBSERVER_CONCURRENCY = 32`). **This is the only behaviour change for existing
  bundles.** `observerExecutor(Executor)` still exists as a compat overload wrapping
  `UnboundedConsumerExecutor`. Observer dead-event replay also moved from async to inline
  (operator-driven and low-volume; sharing the executor would let a replay storm starve
  live consumption).

**Sagas are excluded** — `@SagaEventHandler` has no `executor` attribute, so parallel saga
dispatch is unrepresentable rather than rejected at runtime. Saga handlers are a
read-modify-write on shared state.

### Phase 2 — operability

- **Degraded backoff.** Async handlers fail on their own threads, so their failures never
  surfaced as the fetch loop's `hasError` — a downed dependency was met by the loop pulling
  at full speed and dead-lettering the whole stream. `ConsumerProcessor` now keeps a
  per-consumer **transient-failure streak** (raised by `isTransient` causes, cleared by the
  first clean completion, deliberately *not* raised by permanent failures — a poison event
  is no reason to slow the stream), and both engines back off on the channel-error curve
  while it is non-zero.
- **Counters.** `ConsumerExecutorStats` (`admitted`/`rejected`/`completed`/`failed`/
  `inFlight`) behind a `default` `ConsumerExecutor.stats()`; **`rejected` is the one to
  alert on** — it is the executor announcing it is the bottleneck. Consumer status gained
  `asyncInFlight` / `asyncSubmitTimeouts` / `asyncTransientFailures` / `asyncExecutors`
  (additive and wire-safe: `AdminPayloadCodec` sets `FAIL_ON_UNKNOWN_PROPERTIES=false`).
- **No Micrometer in the bundle** — it is an `evento-server` dependency, and pulling it into
  `evento-bundle` would push a transitive dep onto every user app. The server can bind the
  SPI counters in Phase 3.

### The JDBC / virtual-thread question is now measured, not assumed

`VirtualThreadJdbcConcurrencyIT` (Testcontainers Postgres, `EVENTO_RUN_JDBC_IT=true`):
**32 transactional handlers, `peak=32/32`, 303 ms vs 8000 ms serial, on an 8-core box** —
full capacity at 4× the core count, so no carrier pinning (JDK 24's JEP 491 plus pgjdbc /
HikariCP off `synchronized`). `ConsumerExecutors.pooled` is no longer the recommended
default for transactional handlers.

**Keep this one:** the first run failed at `peak=16` and it was *not* pinning — Hikari opens
connections lazily, and establishing 32 costs far more than a 250 ms handler, so early
handlers finished before late ones held a connection. Pre-warming gives 32/32. The
operational read-across is real: **a freshly started async consumer is throttled by a cold
pool, not by its executor capacity**, until the pool ramps. Tune `minimumIdle` if a
consumer's catch-up burst matters.

### Phase 3 — visibility, and the bug it nearly shipped

`@EventHandler.executor` now travels `RegisteredHandler` → `BundleDiscoveryInfo` →
`AutoDiscoveryService` → `Handler` entity (+ `schema.sql` column with an
`ALTER TABLE … ADD COLUMN IF NOT EXISTS` for live upgrades) → `HandlerDto` → GUI. The
component catalog shows an `Executor:` chip per handler; Cluster Status → Consumers gains a
*Parallel* row (executor names, in-flight, saturation, transient failures) that renders only
when the consumer actually has async handlers. The consumer-status counters needed no server
work — `ConsumerController` returns the message directly. Note `executor` is **refreshed, not
backfilled**, unlike `line`/`componentPath`: a redeploy can move a handler between inline and
async, so backfill semantics would pin the first value seen and show stale data forever.

**⚠️ The repo-wide lesson (now ARCHITECTURE §15): never add a derived getter to a wire DTO.**
A convenience `isAsync()` on `RegisteredHandler` made Jackson emit an `async` property; the
transport `payloadCodec` deserializes with `FAIL_ON_UNKNOWN_PROPERTIES` **enabled** (unlike
`AdminPayloadCodec`/`ObjectMapperUtils`, which disable it), so the server rejected the whole
`BundleDiscoveryInfo` with `decode failed for BundleDiscoveryInfo … Unrecognized field
"async"`. The bundle still started, connected, enabled and consumed perfectly — **only its
handler metadata silently vanished from the dashboard**, with nothing in the bundle logs.
Every pre-existing test passed, because none asserted that discovery *arrived*.
`AsyncConsumerIT.discoveryNotificationDecodesWithTheNewField` now guards it.

**Follow-up: both transport codecs are now lenient** (`fix(transport)`). `JacksonCborCodec`
and `JacksonCborPayloadCodec` disable `FAIL_ON_UNKNOWN_PROPERTIES`, matching what
`AdminPayloadCodec` and `ObjectMapperUtils` already did — so all four agree. This makes
mixed-version fleets work in both directions: a newer peer's extra fields are skipped, and
an older peer's missing fields fall back to the defaults the records' `@JsonCreator`
constructors already normalise. It matters most on the message codec, where a version skew
would have broken the **handshake** itself.

I had originally noted strictness as deliberate gadget-chain defence — that was wrong.
`FAIL_ON_UNKNOWN_PROPERTIES` is not a security control: the defence is `MessageTypeRegistry`
/ the `PolymorphicTypeValidator`, which bound *which types* may be instantiated. Skipping an
unrecognised property on an already-whitelisted type instantiates nothing. Both whitelists
are untouched. `CodecVersionToleranceTest` (evento-transport-api) pins newer-peer,
older-peer and round-trip.

### Phase 4 — the two guarantees given back

Phases 1–3 traded ordering and at-least-once for parallelism. Phase 4 offers each back
independently, so a consumer buys the guarantee it needs and keeps the throughput.

**`CheckpointMode.WATERMARK`** (`setCheckpointMode`, or `setComponentCheckpointMode` per
projector) persists the highest *contiguous completed* sequence instead of the dispatch
frontier, so in-flight events are replayed after a crash rather than lost. The design turns
on separating two cursors that `ON_START` conflates: the **fetch cursor stays on the
in-memory dispatch frontier** while only the *persisted* checkpoint lags. Fetching from the
lagging checkpoint — the obvious implementation — would reprocess the in-flight window on
every single cycle. The commit runs on the consume-loop thread inside a `finally` so all
four exits are covered (end of batch, saturated-executor return, consumer-disabled
short-circuit, thrown `TransientConsumerException`); handler threads only record
completions, which keeps the optimistic-version dance single-threaded. A dead-lettered event
counts as completed — it is resolved, and treating it otherwise would let one poison event
pin the watermark forever. A *stuck* handler does pin it, so the processor warns past
`watermarkLagWarnThreshold` (10 000). Note the dashboard's "last event" now trails true
progress by the in-flight window under this mode.

**`ConsumerExecutors.partitioned(name, lanes)`** is the bigger practical win: same-aggregate
events apply in sequence order while different aggregates run in parallel, which covers
read-model projectors that are *not* idempotent but are per-aggregate sequential. Deliberately
an **executor type rather than an annotation flag** — the consume loop always passes the
aggregate id as ordering key, `submit(task, orderingKey, waitFor)` is a `default` that ignores
it, and only the partitioned impl acts. Opting in is one config line, no handler change, and
third-party `ConsumerExecutor` impls are unaffected. A busy lane **refuses admission rather
than queueing**, preserving both invariants the design rests on (admission means "started";
no unbounded internal queue) — so a burst on one hot aggregate serialises, which is the
guarantee working, not a defect. Size lanes well above the expected count of
concurrently-active aggregates; keyless events spread round-robin.

### Micrometer binding (the last open item, now closed)

Bundles push a `ConsumerStatsMessage` on the existing admin notification channel — the same
one carrying performance metrics and consumer registration — and
`ConsumerMetricsRegistry` (`com.evento.server.bus.spring`) binds it to Micrometer, so the
counters land on `/actuator/prometheus`.

**Push, not poll.** The counters live in bundle-side objects (executor permits, per-consumer
trackers) the server cannot see; having it request them per scrape would turn one scrape
into a round-trip per consumer across the cluster. Interval defaults to 30 s
(`setConsumerStatsInterval`), and a bundle with **no** executor registered pushes nothing at
all, so applications that never opted into parallel consumption pay nothing.

- `evento.consumer.executor.{capacity,in.flight,admitted,rejected,completed,failed}`
  tagged `bundle,instance,executor`; **`rejected` is the alerting signal.**
- `evento.consumer.async.{in.flight,submit.timeouts,transient.failures}` tagged
  `bundle,instance,consumer,component`.
- **Meters are removed on `BusEvent.NodeLeft`.** Without that a rolling restart leaves a
  live series per dead instance for ever — the usual way this kind of gauge becomes a
  cardinality leak. `ConsumerMetricsRegistryTest` pins the removal.
- The registry is injected into `BundleAdminNotificationListener` as an `ObjectProvider`, so
  a context without a `MeterRegistry` (the listener's own wiring test scans only its
  package) still wires. Telemetry must never be why the notification channel fails to start.

**Nothing outstanding on this feature.**

## Dependabot backlog swept (2026-07-25)

**Outcome: backlog empty.** 17 PRs triaged, then a further 14 raised mid-session by the
weekly run. All resolved — nothing Dependabot-related is left open.

### The recurring lesson: Dependabot splits units that must move together

Three separate failures this session had one shape. Dependabot tracks each dependency (or
each *action step path*) independently, but several of ours are one logical unit whose
members pin each other. Every split PR is red on its own, and no combination of them
resolves — the fix is always one commit covering the whole family:

| Unit | Why it cannot split | Landed as |
| --- | --- | --- |
| `codeql-action/init` + `analyze` | share one run; init stamps a config file analyze reads back | #186 |
| The Angular family | runtime packages peer each other on an *exact* version (`peer @angular/core@"22.0.8"`) | #193 |
| `typescript-eslint` + `@typescript-eslint/{eslint-plugin,parser}` | meta-package and plugins pin each other | #193 |

The Angular case is the nastiest: Dependabot raised `common`/`router`/`animations`/
`platform-browser-dynamic`/`cli` but **never raised `core`, `compiler`, `forms`,
`platform-browser`, `compiler-cli` or `@angular-devkit/build-angular` at all**, so merging
every PR it offered still would not have produced a resolvable tree. When a bump fails with
`ERESOLVE` naming a sibling package, bump the whole family in one commit and regenerate the
lock.

**Regenerating `evento-gui/package-lock.json` needs a cross-platform check.** `npm ci` runs
on Linux in CI; regenerating on Windows can drop Linux optional binaries. Diff platform
entries against the previous lock before committing — #193 kept all 32 (esbuild, rollup,
parcel-watcher, glibc *and* musl) and lost only `@parcel/watcher-win32-ia32`, which nothing
resolves. Also re-verify after any rebase: a textually merged lockfile can be incoherent.

### The other structural finding

The dominant finding on the original 17 was structural, not per-dependency:
**11 of the 17 were based on orphaned history.** Their parent was `8b21c506
chore(release): 2.3.0`, which is no longer an ancestor of `main` — `main`'s history was
rewritten after 2026-07-18. Git therefore fell back to an ancient merge base
(`dabdf736`) and GitHub computed their diffs as ~300 files *added*; merging any of them
would have replayed deleted history over `main`. This is what `mergeable: UNKNOWN` on
every npm/github-actions PR actually meant. They were fixed with `@dependabot recreate`,
not merged. **If `main` is ever rewritten again, expect the same failure mode and
recreate the open Dependabot PRs rather than trusting their diffs.**

- **Merged** (all were correctly based on `59f249aa`, all five checks green): log4j-slf4j2-impl
  2.24.3→2.26.1 (#158), jackson 2.18.2→2.22.1 (#160), postgresql 42.7.11→42.7.13 (#168),
  jazzer-junit 0.24.0→0.30.0 (#169), gradle-wrapper 9.3.0→9.6.1 (#166). `CLAUDE.md`
  toolchain line updated to Gradle 9.6.1 to match.
- **netty 4.1.124→4.2.16 (#167)** conflicted after the above merges (its branch carried the
  stale adjacent `jacksonVersion` line); rebased so 4.2 gets a clean CI run on top of the
  other five. 4.2 is a minor in name only — new IoHandler/EventLoop architecture — so it
  was deliberately merged last and left to full CI rather than admin-bypassed blind. It
  passed the full suite, including the real-TCP transport ITs, and is now on `main`.
  **`evento-transport-netty` is the module to check first if anything transport-shaped
  regresses.**
- **Recreated** against current `main`, then merged once green: #174, #172, #164, #163,
  #162, #161. #164's "failure" was misleading — setup-node 7 was fine, the red check was
  the known-intermittent `BusLifecycleDisconnectIT`.
- **`codeql-action` needs `init` and `analyze` bumped in one commit** (#159 → #186, merged).
  This one is worth remembering because it recurs. The two steps share a single CodeQL run:
  `init` writes a config file stamped with its own version and `analyze` reads it back, so
  bumping either alone fails the job with

  ```
  Loaded a configuration file for version '4.37.3', but running version '4.36.0'
  analyze post-action step failed
  ```

  Dependabot tracks the two step paths *independently* and will happily raise them as
  separate PRs (it did exactly that again as #186 `init` / #180 `analyze`), so neither PR
  can go green on its own. The fix is to put both SHAs in one commit. `scorecard.yml`'s
  `upload-sarif` is pinned separately and is genuinely independent — it does not share a run
  with the `codeql.yml` pair.

  Process note: pushing that fix onto the Dependabot branch, combined with an
  `@dependabot recreate`, made Dependabot close #159 and supersede it with #186 on the same
  branch — the commit survived, but the PR number changed underneath. If a hand-fix on a
  Dependabot branch is needed again, either push *or* recreate, not both.
- **Closed as upstream-blocked**, with `ignore` rules added to `.github/dependabot.yml` so
  they stop reappearing: apexcharts 5→6 (#170, `ng-apexcharts@2.4.0` latest still peers
  `apexcharts@^5.10.3`) and eslint 9→10 (#173, `eslint-plugin-import@2.32.0` latest still
  peers `eslint@"…||^9"`). Neither is fixable from this repo. Remove the ignore entry when
  the blocking package ships support.
- **ngx-translate 17→18 migrated** (#171 + #165 → #194, merged). Both packages had to move
  together (`http-loader@18` peers `core@">=18.0.0"`), and v18 **removed `TranslateModule`**.
  The `gui-build` log looked far worse than the fix: the missing export cascaded into
  `NG8004` (no `translate` pipe) and `NG8001` on unrelated `ion-*`/`app-*` elements — one
  root cause across 19 files. `TranslateModule.forRoot({...})` in `AppModule.imports` became
  `provideTranslateService({...})` in `AppModule.providers` (same config shape;
  `provideTranslateHttpLoader` is unchanged in v18), and every other `TranslateModule` in an
  `imports` array became the standalone `TranslatePipe` / `TranslateDirective`.

  Two things to know if this is ever revisited:

  - **`AppComponent` is the trap.** Its template uses both the pipe and the directive, and
    under v17 it inherited them from the `forRoot()` call sitting in `imports`. Moving that
    call to `providers` silently removes them, so `AppModule` must now import both
    explicitly. Every *other* module is fixed by the mechanical substitution, so this is the
    one that gets missed.
  - **`src/assets/i18n/*.json` use flat dotted keys** (`"catalog.payload.title": "Payloads"`),
    not nested objects. v18's `getValue` accumulates the compound key when a segment does not
    resolve, so flat files still work — but a resolution change here would render every
    string as a raw key *with a green build*. Compilation does not prove i18n works: verify
    at runtime (a headless render of `ng serve` showing `login.submit` → "Sign in" covers
    loader fetch, provider wiring, flat-key lookup and the pipe in a migrated NgModule).

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

## Prod hang: unbounded channel-close wait wedged bus workers (2026-07-22)

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
