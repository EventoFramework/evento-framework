# Changelog

All notable changes to Evento Framework are documented here.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added

### Changed

- **`PgDistributedLock` waits are bounded and a lost lock is an error.** `acquire` used to
  wait forever; it now throws `LockAcquisitionTimeoutException` after 60 s
  (`-Devento.lock.acquire.timeout.ms`). `lockedArea`/`tryLockedArea` throw
  `LockLostException` when the lock turns out to have been lost, even if the section
  succeeded (its effects are applied but may have overlapped another holder). The
  server's callers (bundle discovery, consumer registration) catch and log both, as they
  did any database error; a discovery that times out is logged instead of hanging a
  thread, and is redone on the bundle's next reconnect. `tryAcquire` returns `false`
  when no lock connection is available in time.
- **Distributed locks use a connection pool of their own** (`evento.lock.pool.maximum-size`,
  default 16; `evento.lock.pool.connection-timeout-ms`, default 1000). It copies the
  application pool's settings. The application pool default is 20. Budget
  `servers x (20 + 16)` under PostgreSQL's `max_connections`.
- **Upgrade note, `es__events` primary key:** the first server of this version to boot
  builds a unique index on `es__events(event_sequence_number)` with
  `CREATE INDEX CONCURRENTLY` (reads and appends continue; it takes as long as a scan of
  the table), then attaches it as the primary key, which needs `ACCESS EXCLUSIVE` only
  for an instant (`lock_timeout` 3 s, retried). Other booting servers wait for it on the
  schema lock.

### Fixed

- **`WATERMARK` bookkeeping cost grew with the numeric width of a gap (G10).** Since
  2.4.6 the processor closes every sequence number a fetched batch proves absent — but it
  did so one number at a time into a `TreeSet<Long>`. A consumer filtered to one context
  of seven added ~6/7 of the shared sequence to the set on every batch, and a
  Snowflake-numbered store (APES, ids ~4.2 billion apart per millisecond) turned a single
  gap into a loop that hung the consumer or exhausted the heap. Resolved numbers above the
  watermark are now kept as disjoint intervals (`SequenceRangeSet`): each gap is closed in
  one O(log n) step and memory is bounded by the handlers still in flight, not by how
  sparse the stream is. Semantics are unchanged — every 2.4.6 `WatermarkCheckpointTest`
  case passes as is — and new cases pin a 1-in-7 context-filtered consumer with a held
  handler (≤ 2 intervals over ~70,000 resolved numbers) and Snowflake-scale gaps under a
  5 s timeout.
- **A consumer checkpoint could go backwards (G11).** A lock can be lost without its
  holder noticing (partition, database failover); the stale holder's next commit failed
  the version check, and the processor's drift path re-read the version and committed its
  older position anyway, rewinding the lane for the new owner. `ConsumerStateStore.commit`
  is now monotonic by contract: the JDBC store writes
  `last_sequence = GREATEST(last_sequence, ?)` (Postgres and MySQL), the in-memory store
  does the same, and the processor's drift path no longer commits at all when the stored
  position is already at or past its own. Only `delete` lowers a position. JDBC ITs on
  both databases show a stale replica with its own connection cannot rewind the row, and
  8 replicas retrying on drift converge on the highest position.
- **`PgDistributedLock`: one waiter could stall every lock of a server, and a lost lock
  went unnoticed (G3).** Each instance pinned a single connection and blocked in
  `pg_advisory_lock` on it, so a thread waiting on a key held by another server queued
  every other acquire *and release* of that instance behind it (two servers could
  deadlock each other until `socketTimeout`), and when that session died PostgreSQL
  dropped all its locks while the JVM still believed it held them. Each held lock now
  owns its own connection (borrowed on acquire, unlocked and returned on release) from
  a pool dedicated to locks, so holders can never pin the connections their own work
  needs (with a shared pool, as many locked commands as connections all timed out in
  `getConnection()` together); waiting polls `pg_try_advisory_lock` with jittered backoff, holds no
  connection between attempts, and is bounded (60 s default,
  `-Devento.lock.acquire.timeout.ms` or the new constructor) with
  `LockAcquisitionTimeoutException`, and a pool timeout counts as a failed attempt.
  `verifyHeld(key)` checks `pg_locks` on the owning session (not just that the connection
  is alive) and `release(key)` detects a lost lock; both log `event=lock_lost` and throw `LockLostException`;
  `CommandBrokerHandler` calls `verifyHeld` right before appending, so a command whose
  lock died while the bundle computed it is refused instead of forking the aggregate,
  and a release failure after a successful append no longer replaces the reply. The
  check narrows the fork window but cannot close it; that needs the G6 `aggregate_seq`
  fence. A failure right after the lock was granted no longer returns the lock-owning
  connection to the pool (which would have kept the key locked for good).
- **CPES appends cost one round trip instead of four (G9).** The global `es-lock`,
  `nextval` and the `INSERT` ran as separate autocommit statements on two connections.
  They are now a single statement (`pg_advisory_xact_lock` in a materialised CTE +
  `INSERT ... SELECT nextval(...) RETURNING`), i.e. one implicit transaction whose lock
  is released only after the commit is visible, so sequence order still equals commit
  order and the lock cannot outlive a dead session. Same lock key as before, so old and
  new servers exclude each other during a rolling upgrade. Measured through a 100 ms
  delay proxy: ~4.2 round trips before, ~1.1 after (`EventStorePostgresIT`).
- **`es__events` has a primary key (G6, PK part).** Added idempotently on boot, without
  blocking the event store: `CREATE UNIQUE INDEX CONCURRENTLY`, then
  `ADD PRIMARY KEY USING INDEX` under a short `lock_timeout` (see the upgrade note under
  Changed). Existing duplicate sequence numbers fail the boot with their count and the
  query to find them, rather than being skipped silently;
  `evento.es.schema.enforce-primary-key=false` is the explicit escape hatch.
- **Servers starting together no longer abort on `schema.sql` (G14).** Concurrent runs
  raced on the catalog (`pg_type_typname_nsp_index`, `tuple concurrently updated`);
  unlocked, 3 servers on a fresh database failed 10 rounds out of 10 in a probe. The SQL
  initializer now runs under a session advisory lock, taken by polling so a waiting
  server holds no snapshot the index build would wait for (`AdvisoryLockedSchemaInitializer`,
  replacing Boot's `DataSourceScriptDatabaseInitializer`; `spring.sql.init.*` unchanged).
- **Context filters no longer treat `_` and `%` as wildcards (G15).** `SUD_AMERICA`
  also matched `SUDXAMERICA`. LIKE metacharacters are escaped before `*` becomes `%`,
  and the fetch queries declare `ESCAPE '!'` (a backslash escape would depend on
  `standard_conforming_strings`).
- **Aggregate caches are thread-safe and honour their TTL (G19).** `ExpiringLruCache`
  was an unsynchronised access-ordered `LinkedHashMap` read through the TTL-bypassing
  `containsKey`/`getOrDefault`; it now wraps the map, synchronises every operation and
  only offers TTL-aware reads. The cached event list is copied, not appended to in place.
  Cross-server invalidation after `deleteAggregate` is still local only.
- **A stale `NodeLeft` on one server wiped a bundle instance that had failed over to
  another (G7).** Servers that share a catalog each see only their own connections. When
  instance I moved from server A to server B, A noticed the dead socket up to 45 s later,
  cleared I's `core__consumer` rows and unregistered the whole bundle cluster-wide,
  undoing B's fresh registration. A new `core__bundle_instance` table (created
  idempotently by `schema.sql`) records which server holds each instance's session: the
  server claims the row on `NodeJoined`, and a leave cleans up only when this server
  still owns it. The "another instance is still live" check now also counts instances
  held by other servers. The last instance out reclaims an ephemeral bundle whichever
  instance the row names, so a bundle no longer leaks when the recorded instance leaves
  first. A restarted server purges the rows it held before. Each server refreshes its
  rows every 30 s (`evento.server.presence.heartbeat-interval-ms`). Rows older than
  3 min (`evento.server.presence.stale-after-ms`) no longer count as live, so a server
  that crashes and never returns under the same id cannot keep its bundles in the catalog
  for ever. A leave re-checks the local view after releasing: a channel-close `NodeLeft`
  of an old session can be published after the new session of the same instance has
  already claimed, and it must not delete that live row. Ownership needs a unique
  `evento.server.bus.server-instance-id` per server. The server logs a WARN at startup
  when it runs with the shipped `iris-evento-server` id.
- **Concurrent discovery on several servers failed on `duplicate key` (G8).** Discovery
  and consumer registration created bundle, component and payload rows find-then-insert,
  under different lock keys. Payload and component names are shared across bundles, so no
  per-bundle lock covered them, and a mass reconnect aborted passes half-written on
  `core__*_pkey`. Those rows are now created with `INSERT ... ON CONFLICT DO NOTHING`, and
  consumer rows are upserted. Consumer registration takes the same `DISCOVERY:<bundleId>`
  lock as discovery and leave. Each discovery pass, leave cleanup and consumer
  registration runs in one transaction. `BundleService.unregister` scans only the leaving
  bundle's handlers; before, it raced the removal of other bundles and hit handlers whose
  component was already gone. The orphan-payload sweep runs after the commit, one
  best-effort delete at a time. Payload rows are shared across bundles, so the discovery
  transaction never writes them: every referenced payload is created up front in name
  order, one statement each, and type corrections and `payloadInfo` metadata are applied
  after the commit, again in name order and only when a value changes. Inside the
  transaction the reused payload rows are pinned `FOR KEY SHARE`, so a concurrent orphan
  sweep cannot delete one before the new handler rows are flushed. A pass that still
  loses a race (deadlock, serialization failure, foreign key violation, or a row that
  vanished) is retried up to three times.
- **The consumers page cut contexts that contain `_` (G16).** `ConsumerController` split
  the consumer id on `_`, so `SUD_AMERICA` showed as `SUD`, and a bundle id containing
  `_` shifted every field. `ConsumerIds` strips the known `bundleId_component_` prefix
  and reads `version_context` from the rest. The page now lists one entry per consumer id
  rather than per component, so each context lane of a multi-context component shows up.
  After a component version bump, the consumers of the old and the new version show as
  separate entries until the old instances leave; before, they were merged per component.
- **Every server ran the hourly telemetry TTL sweep.** It is now guarded by an advisory
  try-lock, so one server per hour runs it.

### - **Broker endpoint failover for bundles (G2).** `BundleClientConfig` / `BundleClient.Builder`

  take an ordered endpoint list (`endpoint(host, port)` / `endpoints(...)`, most preferred
  first); `host`/`port` keep working as a one-element list. `EventoBundle` now passes
  *every* address of `EventoServerMessageBusConfiguration` — before, addresses 2..n were
  silently ignored and a bundle whose broker died stayed stranded next to two healthy ones.
  `ConnectionSupervisor` moves to the next endpoint after a session that fails before READY,
  and restarts from endpoint 0 after a READY session drops (fail-back happens at the next
  disconnect; there is no background probe). Backoff counts passes over the list, so the
  second broker is tried after one base delay; `start()` fails only once every endpoint was
  tried. `event=handshake_complete` now logs `endpoint=` and `cluster=`.
- **`BrokerChangedException` (G4).** Each pending request remembers the broker it was sent
  through. When a new session's `Welcome` names a different `serverInstanceId`, those
  requests fail at once (a failure `Response` naming `BrokerChangedException`, rethrown as
  such by `EventoServerAdapter`) instead of hanging for the full request timeout: the old
  broker buffers their replies and replays them only to a bundle that comes back to *it*.
  It extends `RequestTimeoutException` because the outcome is equally indeterminate.
  Reconnecting to the same broker keeps the survive-reconnect behaviour.
- **Cluster identity in the handshake (G13).** `Welcome` gains an additive `clusterName`
  (from `evento.cluster.name`; empty when not announced, older peers decode it unchanged),
  and bundles gain `expectedClusterName` (`BundleClient.Builder`, `BundleClientConfig`,
  `EventoServerMessageBusConfiguration.withExpectedClusterName`). A broker announcing another
  cluster, or none, is refused before registration and the bundle keeps dialing the next
  endpoint (`event=cluster_name_mismatch`). With an endpoint list use this rather than
  `expectedServerInstanceId`, which would refuse every failover target; give each server a
  unique `evento.server.bus.server-instance-id`, since G4 relies on it.
- **Link settings on `EventoBundle.Builder` (G22).** `setTransportConfig`,
  `setHeartbeatReadIdle` (also lowers the Ping cadence to a third when needed),
  `setReconnectStrategy`, `setDefaultRequestTimeout`, `setMaxInFlightRequests`; plus
  `NettyTransportConfig.withHeartbeat` / `withReconnectStrategy` and
  `BundleClient.Builder.maxInFlightRequests`. The 45 s read-idle — how long a silent
  partition goes unnoticed — was not reachable from the bundle before.

### - **Locality-aware handler selection (G12).** A bundle can declare where it runs with

  `EventoBundle.Builder.setZone("EU")` (or `BundleClient.Builder.zone(...)` /
  `BundleClientConfig.Builder.zone(...)`). The zone travels as a `zone:<ZONE>` entry in the
  existing `Hello.capabilities` set — no new wire message or field, and a broker that knows
  nothing about zones ignores it. A broker started with
  `evento.server.bus.pick-strategy=locality-preferred` (new `ClusterRegistry.PickStrategy.LOCALITY_PREFERRED`)
  routes each request to an available handler in the same zone as the bundle that sent it and
  falls back to any available handler when that zone has none, so a dead region costs latency
  rather than availability. Commands brokered by `CommandBrokerHandler` pick on behalf of the
  original sender through the new `BusLifecycle.OriginatorAwareRequestHandler`. The default
  stays `random`. The zones a server knows are listed at `GET /api/cluster-status/zones`.
  `LocalityPickIT` runs three handler replicas (EU, AM, APAC) and an AM caller over real TCP:
  every call stays in AM until the AM replica is killed, then spreads over EU and APAC.

### Documentation

- **Context naming and matching (G15, client side).** `EventoBundle.Builder.setComponentContexts`
  now documents that `*` is the only wildcard in a context value and that servers without the
  G15 fix treat `_` and `%` as `LIKE` wildcards too (`SUD_AMERICA` also matches `SUDXAMERICA`).
- **Commands carry no framework-level context (G23).** The stale `DomainCommand` javadoc that
  described a `PayloadWithContext` base class is replaced by what is true: only `Event` has a
  context, and an aggregate that emits region-scoped events copies the context from a command
  field into the event.

---

## [2.4.6] — 2026-09-05

### Fixed

- **A burned sequence number pinned every `WATERMARK` projector for ever.** The event
  store's sequence is non-transactional: an insert that rolls back keeps the number it
  drew and the store never returns it. `InFlightTracker.advanceWatermark()` walked a
  strictly contiguous prefix, so the first such hole stopped the persisted checkpoint at
  the number before it — while the in-memory fetch cursor went on. Read models stayed
  current, so nothing looked wrong; but every restart replayed the whole gap (124k events
  x 12 projectors in the case that surfaced it), which was enough to OOM-kill a 2 GB
  broker, and the checkpoint never recovered without a manual `UPDATE`. The processor now
  closes, from each fetched batch, every number between the cursor and the batch's last
  event that the store did not return — a hole is proven by the batch that skips it, and
  nothing else will ever complete it. Numbers above a batch are not assumed.
  `WatermarkCheckpointTest` pins the cold-start shape (checkpoint N, first existing row
  N+2), holes mid-batch, holes revealed only by a later fetch, and that a hole never
  releases an event that is still running. The `CheckpointMode` javadoc no longer claims
  inline consumers behave identically in both modes — they do not, in exactly this case.

### Added

- **`expectedServerInstanceId` on `BundleClient` / `EventoServerMessageBusConfiguration`
  (`withExpectedServerInstanceId`).** A bundle dials a host name; two brokers reachable
  under the same name — two compose projects on one Docker network, each calling its
  broker `evento-server` — are indistinguishable at the transport, and in production a
  bundle registered with a demo's broker during a 22-second outage of its own and spent
  an hour and forty minutes persisting a shop's events into the wrong event store while
  looking perfectly healthy. The `Welcome` carries the broker's `serverInstanceId`; when an
  expected id is configured and the announced one differs, the supervisor logs
  `event=server_instance_mismatch`, refuses **before** registration, and keeps dialing
  with the usual back-off — a wrong broker is treated like an unreachable one. Unset, the
  behaviour is unchanged. Give each broker its own `evento.cluster.node.server.id` for this
  to mean anything. `BundleClientIdentityIT` covers refusal, acceptance and the unset case
  over real TCP.

---

## [2.4.5] — 2026-08-06

### Fixed

- **2.4.4 broke bundle startup under Spring Boot's dependency management.** Boot's BOM pins
  `io.netty:*` to the 4.1 line inside user applications regardless of what
  `evento-transport-netty` declares, and 2.4.4's internal migration to Netty 4.2 APIs used
  `MultiThreadIoEventLoopGroup`, which does not exist in 4.1 — every unpinned Boot app died
  at startup with `NoClassDefFoundError` while constructing the bundle. Event-loop
  construction is back on the 4.1-compatible `NioEventLoopGroup` (deprecated in 4.2 but
  present and functional in both lines), and the transport test suite now also runs with
  Netty forced to 4.1 in CI (`netty41Test`) so a 4.2-only API can never ship again.
  Workaround on 2.4.4: `ext['netty.version'] = '4.2.16.Final'` (Gradle) or the
  `<netty.version>` property (Maven).

---

## [2.4.4] — 2026-08-05

### Fixed

- **`PgDistributedLock.release()` race** — under concurrent publishing through one lock key
  (the event store funnels every service command through `"es-lock"`), a thread that
  legitimately held the lock could get `IllegalMonitorStateException: No lock held for key`
  on release: the decrement of the wrapper's queue count and its removal from the shared map
  were two separate steps, so a concurrent `acquire` could join a wrapper about to be
  unmapped. Thrown from `lockedArea`'s cleanup, the error also replaced the critical
  section's real outcome (a successfully published event surfaced to the caller as a
  failure) and leaked the PostgreSQL advisory lock. Decrement-and-unmap is now atomic, and
  `lockedArea` no longer lets a cleanup failure mask the critical section's result — it is
  attached as suppressed to an in-flight exception, or logged after a success.
- The release scripts read the version with a pattern broken by the Gradle assignment-syntax
  change below; both now tolerate either style.

### Changed

- **GUI build system** — `evento-gui` moved from the deprecated webpack `browser` builder to
  Angular's esbuild `application` builder: production build 64s → 16s, initial bundle
  ~2 MB → 265 kB raw (Ionic components now lazy-load as ~115 code-split chunks). No
  behaviour change for the embedded GUI; output still lands in the server's `public/`.
- Internal API modernisation, no user-facing change: Netty 4.2 non-deprecated APIs
  (`MultiThreadIoEventLoopGroup`, `WRITE_BUFFER_WATER_MARK`), Spring `JdbcTemplate` varargs
  query overloads, Gradle 10 assignment syntax across all build files, Angular 22.1 /
  Ionic 8.8.17 / jsdom 30 dependency bumps.

---

## [2.4.3] — 2026-07-28

### Changed

- **The projector head-reached startup gate is now opt-in.** The bundle no longer waits for
  projectors to align with the event-stream head before enabling: default is
  `@Projector(waitForHeadReached = false)` — the bundle enables immediately after
  registration and projectors catch up in the background. Annotate
  `@Projector(waitForHeadReached = true)` to restore the old gate for projectors whose
  queries must not be served from a behind-head read model.

### Fixed

- `EventoBundle` component scanning now respects `setBasePackage` exactly: previously every
  class sharing the base package's classpath *roots* was scanned too, so two bundles in one
  jar could never be isolated.

---

## [2.4.2] — 2026-07-27

Re-cut of 2.4.1 after its release pipeline run failed; no code changes.

---

## [2.4.1] — 2026-07-27

### Fixed

- **A broker OOM now kills the process instead of leaving a zombie** (production incident
  2026-07-27: an `OutOfMemoryError` during event-fetch handling killed the Netty worker
  event loops; every `catch (Throwable)` swallowed it, and the broker survived three hours
  accepting TCP connections it could only force-close — the container restart policy never
  fired because the JVM never exited). Three layers:
  - `-XX:+ExitOnOutOfMemoryError` in the evento-server Docker entrypoints;
  - a default uncaught-exception handler in `EventoServerApplication` that halts on
    `VirtualMachineError` (covers threads whose infrastructure swallows errors internally);
  - `FatalErrors.escalateIfFatal(t)` (new, `evento-common`) called first in the broker's
    catch-all handlers (`BusLifecycle`, `BusEventBus`, `ConnectionRegistry`) — walks the
    cause chain, halts with exit code 3 on a fatal error; disable with
    `-Devento.fatal.halt=false` (tests).
- **Stale `EventFetchRequest`s are dropped instead of served** — a consumer abandons a
  fetch after its client-side timeout and immediately retries, so a request older than
  `evento.es.fetch.max.age.ms` (default 30000; ≤0 disables) is rejected after the
  concurrency permit is acquired rather than doing the full result-set + encode work for a
  reply nobody reads. Under memory pressure that retry storm repeated the broker's heaviest
  work exactly when it could least afford it. Unstamped requests (older clients) are never
  dropped.
- **Reconnecting no longer trips an FK violation in discovery** — `Consumer.component` was
  mapped `@ManyToOne(cascade = ALL)`, so deleting an instance's consumer rows (e.g.
  `ConsumerService.clearInstance` when a connection is superseded) cascaded a REMOVE to the
  shared `core__component` row while other instances' consumer rows still referenced it:
  every rejoin logged `violates foreign key constraint
  "core__consumer_component_component_name_fkey"` and rolled back. The cascade is removed;
  component lifecycle stays with discovery/`BundleService`, which already deletes consumers
  before components.

---

## [2.4.0] — 2026-07-26

### Added

- **Parallel consumers** — an `@EventHandler` may name a bounded *consumer executor*
  registered on `EventoBundle.Builder`, dispatching its events in parallel instead of one
  at a time. `executor = ""` (the default) keeps the existing sequential path, so nothing
  changes for a bundle that does not opt in.
  - New SPI `ConsumerExecutor` + factories `ConsumerExecutors.virtual(name, n)` /
    `pooled(name, n)` / `partitioned(name, lanes)`; register with
    `Builder.addConsumerExecutor(...)`. A name is a capacity budget shared bundle-wide.
  - **The checkpoint advances when a task starts, not when it completes.** That bounds how
    far a consumer can run ahead of completion (never more than the executor's capacity),
    so no unbounded prefix of the event store is ever enqueued, and it makes the crash-loss
    window the set of *running* tasks rather than a queue depth. Admission is time-boxed:
    a saturated executor ends the cycle and releases the consumer lock.
  - **Guarantees relaxed** — no ordering between parallel events, and at-most-once for
    events in flight when the process is killed abruptly (a graceful stop drains). Intended
    for idempotent or overwrite handlers; both guarantees can be taken back, below.
  - **`retry = -1` is coerced to `0` under an executor**, because retrying forever inside a
    task pins a concurrency permit and starves the executor. Set an explicit `retry` for a
    parallel handler that calls a remote dependency.
  - Referencing an unregistered executor **fails bundle start-up** rather than silently
    degrading to sequential execution. Not available on `@SagaEventHandler`.
- **`ConsumerExecutors.partitioned(name, lanes)`** — per-aggregate ordering with cross-
  aggregate parallelism: events sharing an aggregate id are pinned to one lane and applied
  in sequence order. Makes parallel consumption usable by read-model projectors that are
  per-aggregate sequential rather than idempotent. No handler change needed.
- **`CheckpointMode.WATERMARK`** (`Builder.setCheckpointMode` /
  `setComponentCheckpointMode`) — persists the highest *contiguous completed* sequence
  instead of the dispatch frontier, restoring at-least-once delivery at the cost of
  replaying the in-flight window after a crash. The fetch cursor still follows the dispatch
  frontier, so nothing is reprocessed within a run.
- **Transient-failure backoff for parallel consumers** — async handlers fail on their own
  threads, so their failures never surfaced to the fetch loop; a downed dependency was met
  by the loop pulling at full speed and dead-lettering the whole stream. Consumers now
  track a transient-failure streak and back off on the same curve as a channel error.
- **Observability**
  - Discovery publishes each handler's `executor`, so the GUI's Component Catalog marks
    parallel handlers and Cluster Status → Consumers shows a *Parallel* row (executor
    names, in-flight, saturation, transient failures).
  - Bundles push counters to the server, exposed on `/actuator/prometheus`:
    `evento.consumer.executor.{capacity,in.flight,admitted,rejected,completed,failed}` and
    `evento.consumer.async.{in.flight,submit.timeouts,transient.failures}`. **`rejected` is
    the alerting signal** — the executor reporting it is the bottleneck. Meters are removed
    when a node leaves. Bundles with no executor push nothing.

- **Request capacity is now observable and bounded on the request path**, matching what
  parallel consumers already did for the consume path.
  - `RequestTimeoutException` and `TooManyPendingRequestsException` (both
    `com.evento.transport`) replace the generic `IllegalStateException` a bundle used to
    receive for *any* failed request. The distinction is load-bearing: a timeout means the
    caller stopped waiting, **not** that the handler rejected anything — the work may have
    been applied in full. Applications can now report that as indeterminate (504) instead
    of a definite failure (500), and retry only what is safe to retry.
  - `BundleClientConfig.Builder.maxInFlightRequests(n)` (default 2048, `0` disables) refuses
    a request outright once `n` are outstanding, instead of queueing work that will expire
    unsent. The refusal is definite — nothing was transmitted — so it is always retryable.
  - New meters `evento.server.bus.executor.{pool.size,max,active,queue.depth}` and the
    counter `evento.server.bus.executor.saturated`. **`saturated` is the alerting signal**:
    it only moves when the pool is at max *and* the queue is full.
  - Expiries now log at `WARN` (they were `INFO`), and the bundle-side line carries a
    per-payload-type breakdown — `byType={CatalogProductAddCommand=12}` — so the message
    type that is drowning is named rather than merely counted.

### Changed

- **The bus business executor grows before it queues.** A stock `ThreadPoolExecutor` only
  starts a thread beyond `core` once its queue is *full*, so the shipped
  `core=cores×2, max=cores×8, queue=1024` ran on the core size alone until 1024 requests
  were backed up — the configured maximum was unreachable under exactly the load it existed
  for. Because every request carries a client deadline, that backlog did not merely add
  latency: requests expired in the queue and were then served for a caller that had gone,
  so throughput collapsed rather than degrading. `BusBusinessExecutor` inverts the order
  (grow to max, then queue, then `CallerRuns` back onto the event loop), and the queue
  default drops `1024 → 256` because a queue deeper than `deadline ÷ service time` is dead
  work by construction. Observed in production: 20 concurrent writers against an 8-core
  server fell from 16 writes/s to 0.07 writes/s with ~250 timeouts/hour; the same load at 8
  writers sustained 25 writes/s with none.
- **Observers now fan out with a bound.** They always dispatched asynchronously and
  checkpointed on submit, but through an *unbounded* executor — an observer catching up
  from a cold checkpoint submitted its whole backlog as fast as it could fetch it. The
  default is now a bounded executor (32). `ConsumerProcessor.Builder.observerExecutor(Executor)`
  remains as a compatibility overload. **This is the only behaviour change for existing
  bundles.**
- Observer dead-event replay runs inline instead of on the observer executor; replay is
  operator-driven and low-volume, and sharing the executor let a replay storm starve live
  consumption.
- `MessageHandlerInterceptor` documents the thread-affinity guarantee interceptor-managed
  transactions rely on: for one event, `before → handler → after/onException` always run on
  one thread (the executor's task thread under parallel dispatch), so `ThreadLocal`-bound
  transaction managers are unaffected. Implementations must now be thread-safe.

### Fixed

- **Both CBOR codecs ignore unknown properties** (`JacksonCborCodec`,
  `JacksonCborPayloadCodec`), matching `AdminPayloadCodec` and `ObjectMapperUtils`. A peer
  one version ahead previously had its *entire* payload rejected, and the failure was
  near-silent: a bundle would handshake, register, enable and consume normally while all of
  its handler metadata vanished from the dashboard. On the message codec the same skew would
  have broken the handshake itself. This is not a relaxation of the deserialization
  hardening — the gadget-chain defence is `MessageTypeRegistry` / the
  `PolymorphicTypeValidator`, which bound *which types* may be instantiated, and both are
  untouched.

---

## [2.1.0] — 2026-06-06

### Added
- **Confinement check** (`evento-bundle`): registration-time detection of gateway
  call sites that are invisible to static interaction-graph extraction.
  - New `ConfinementScanner`: ASM sweep over every class in the scanned packages
    that is not a registered component; each `CommandGateway.send/sendAndWait` /
    `QueryGateway.query` call site found there is reported (class, method, line,
    kind). Such "gateway leaks" (e.g. an injected helper class issuing commands on
    a handler's behalf) previously under-approximated the extracted emit set
    silently.
  - `AsmInvocationScanner` now also reports gateway calls whose payload is typed
    as the abstract `Command`/`Query` base — the concrete payload type is
    statically unresolvable, so the call is surfaced instead of silently dropped.
  - New `EventoBundle.Builder` flag **`strictConfinement`** (default `false`):
    findings are logged as warnings by default; when set, registration fails with
    `IllegalStateException` on either kind of finding.

### Changed
- `EventoBundle` configures Reflections with an unfiltered `SubTypes` scanner so
  the confinement sweep can enumerate every class in the base package (component
  discovery behavior is unchanged).

---

## [2.0.0] — 2026-05-30

First general-availability release of the v2.0 ground-up rewrite, promoting `2.0.0-rc1` to GA.
Wire-format compatibility with v1 remains **intentionally broken**.

### Added

- **Self-description parity with the (removed) CLI**: bundles publish full discovery metadata at
  startup — component/handler/payload source paths and line numbers, `repositoryUrl` + `linePrefix`
  for clickable source links, and the `@EventoDescription` annotation for human-readable
  descriptions — so the static-analysis publish step is no longer needed.

### Removed

- **`evento-cli`** module deleted — bundle discovery is fully automatic via ASM self-discovery at
  startup; the static-analysis publish / version-bump step is gone.
- **`evento-parser`** module deleted — last consumer of the parser-based `BundleDescription` removed.
- **Deploy-by-upload / autoscaling** surface removed: server no longer accepts JAR uploads, the
  `/spawn` + `/kill` endpoints, `docker-spawn.py`, per-bundle env/VM-options, `autorun`/`deployable`
  flags, and the autoscaling protocol are all gone. Deployment and scaling are owned by the external
  orchestrator (k8s / Nomad); the framework emits performance metrics only.

### Changed

- `TracingAgent` default is now an honest no-op (telemetry/metrics only); wire a custom agent or
  `SentryTracingAgent` for real distributed tracing.
- Public documentation (docs.eventoframework.com) realigned to v2: CLI / deploy-script / autoscaling
  pages removed, consumer-state-store pages rewritten to the five-SPI + JDBC-module model.

### Fixed

- `JdbcConsumerStateStore`: framework-provided `ObjectMapper`; improved optimistic locking on version
  promotion. Docker API-version handling refined.

---

## [2.0.0-rc1] — 2026-05-21

First release candidate of the v2.0 ground-up rewrite. Wire-format compatibility with v1 is
**intentionally broken** — this is a major release.

### Breaking Changes

- `evento-bundle`: v1 transport classes deleted (`EventoSocketConnection`, `EventoServerClient`,
  `ClusterConnection`, `EventoSocketConfig`, `MessageHandler`, `ResponseSender`, `RequestHandler`,
  `EventoResponseSender` — 1 634 LOC removed)
- `evento-server`: `MessageBus.java` (1 099 LOC) deleted; replaced by composable `BusLifecycle`
- Consumer state store: v1 abstract `ConsumerStateStore` class and v1 Postgres/MySQL modules
  removed; replaced by five focused SPIs + JDBC module
- Autoscale protocol removed: `BundleDeployService`, `ClusterNodeIs{Bored,Suffering,Kill}Message`,
  `/spawn` + `/kill` REST endpoints. The external orchestrator (k8s / Nomad) now owns scaling.
- Spring property prefix: `evento.server.bus.v2.` → `evento.server.bus.`
- Gradle module rename: `evento-consumer-state-store-jdbc-v2` → `evento-consumer-state-store-jdbc`

### Added

**Transport layer (`evento-transport-api` + `evento-transport-netty`)**
- Netty 4.1 pipeline: CBOR encoding, transparent chunking (no hard message-size limit), optional TLS
- Sealed `Message` record hierarchy: `Hello`, `Welcome`, `Reject`, `Ping`, `Pong`, `Request`,
  `Response`, `Notification` — compiler-enforced exhaustive dispatch
- `MessageTypeRegistry`: byte-tag whitelist; adding a wire type = extend `permits` + register a tag
- `ConnectionStateMachine`: atomic state transitions; `canSend()` is `true` for `CONNECTED` and `DEGRADED`
- `ExponentialBackoffWithJitter`: base 500 ms, max 30 s, jitter 20%
- `InMemoryTransport`: test double for unit testing without Netty
- `ChunkingEncoder` / `ChunkReassembler`: transparent chunking by stream UUID — no message size limit
- Zero-copy forwarding: `Transport.sendRaw(byte[])` — broker relays without CBOR re-encoding

**Server bus (`evento-server`)**
- `BusLifecycle` orchestrator with explicit `start(port)` / `stop(Duration)` lifecycle
- `ConnectionRegistry`: atomic supersede + token-guarded unregister (prevents post-reconnect routing loss)
- `ClusterRegistry`: `payloadType → Set<NodeAddress>` with RANDOM / FIRST pick strategy
- `CorrelationStore`: bounded shutdown, scheduler-driven expiry
- `ForwardingTable`: `drainByDestination()` preserves originator-side entries for QoS reconnect delivery
- `ForwardingDedupCache`: LRU exactly-once at the broker (50k entries, 5 min TTL)
- `TokenValidator` SPI: `acceptAll()` (default) + `sharedSecret(token)` (constant-time compare)
- `BusEvent` sealed hierarchy replacing v1's four parallel listener lists
- `BusFacade` SPI + `BusLifecycleFacade` adapter unifying server-side consumers
- `CommandBrokerHandler`: restores aggregate/service command interception on top of `BusLifecycle`
- `BrokerEventStore` interface: decouples `CommandBrokerHandler` from JPA `EventStore`
- Split bundle registration: lean `evento:bundle-registration` (bundleVersion + handlerPayloadTypes) +
  rich `evento:bundle-discovery` (handlers + payloadInfo schemas) sent post-enable
- `reconnectBuffer`: buffers in-flight responses for disconnected originators (2-min TTL)

**Bundle client (`evento-bundle`)**
- `BundleClient` façade: Builder, `start/stop`, `request/notify`, handler registration
- `BundleClientState` machine: `INITIAL → CONNECTING → HANDSHAKING → REGISTERING → READY → …`
- `ConnectionSupervisor`: Netty transport + reconnect loop
- `BundleCorrelationTracker`: futures survive disconnect; `failAll()` only on shutdown
- `ProcessedRequestCache`: inbound exactly-once (Claimed / InFlight / Replay)
- `EventoServerAdapter`: implements `EventoServer` over `BundleClient`
- `BundleAdminRequestHandler`: closes dashboard / consumer round-trip on v2 wire

**Consumer engines (`evento-bundle`)**
- `ProjectorEngine`, `SagaEngine`, `ObserverEngine`: compose on `ConsumerProcessor` + SPIs
- `EngineSupervisor`: virtual-thread executor, deadlined `shutdown(Duration)`
- `ConsumerHandle` interface: uniform admin surface for all engines
- `DispatchContext` record: groups tracing + telemetry + interceptor (reduces constructor arity)
- `MessageHandlerInterceptor`: all 24 methods are `default` (ISP fix); implementors override only what they need

**Consumer state SPI (`evento-common`)**
- `ConsumerStateStore`: checkpoint read/commit (optimistic versioning), enabled flag, error history
- `ConsumerLock`: cross-JVM exclusive zone; `LockHandle: AutoCloseable`
- `SagaStateStore`: saga instance lookup by association + insert/update/delete
- `DeadEventQueue`: per-consumer DLQ with retry flag
- `DedupeStore`: observer dedupe with sweep windows
- In-memory impls for all five SPIs (test-friendly, zero setup)

**JDBC state store (`evento-consumer-state-store-jdbc`)**
- Full implementations of all five SPIs for Postgres and MySQL
- Flyway V1 migrations (schema: `evento_v2_consumer_state`, `evento_v2_saga_state`,
  `evento_v2_dead_event`, `evento_v2_dedupe`)
- `JdbcConsumerLock`: Postgres `pg_try_advisory_lock(hashtext(id))` / MySQL `GET_LOCK(id, 0)`
- `JdbcSagaStateStore`: JSON(B) state + flat `associations` column for fast `->> ?` / `JSON_EXTRACT` lookups
- Testcontainers ITs gated on `EVENTO_RUN_JDBC_IT=true`

**Integration test harness**
- `evento-lab`: in-process single-bundle IT harness (`EmbeddedBroker`, `TestEventStoreBundleClient`,
  `CommandAwareEmbeddedBroker`); 31 tests across connectivity, RTT, failure matrix, stress, interceptors
- `evento-lab-microservices`: multi-bundle RECQ microservices scenario (command, query, saga,
  observer, notification, payment saga); 22 tests

### Fixed

- **Fix A**: `onTransportDisconnected` superseded-session race — handlers wiped on bundle reconnect
- **Fix B**: FK violation (`core__handler_return_type_name_fkey`) in `BundleService` orphan-payload
  cleanup; added `HandlerService.isPayloadReferenced()` guard
- **Fix C**: DEGRADED channel dropped in-flight responses — `ConnectionState.canSend()` now
  returns `true` for DEGRADED (TCP socket is alive; Netty backpressure is advisory)
- **Fix D**: TCP disconnect + reconnect lost in-flight responses — `ForwardingTable.drainByDestination()`
  + `reconnectBuffer` + `deliverPendingResponses()` on re-handshake
- `ReflectionUtils.invoke()`: propagates checked exceptions directly instead of wrapping in `RuntimeException`
- `LogTracesMessageHandlerInterceptor`: fixed 4 swapped before/after log messages for Saga/Observer handlers
- `ProjectionReference.invoke()`: fixed silent bug passing `Class<?>` instead of the actual projection instance
- CBOR 20 MB string cap removed from `AdminPayloadCodec` (Jackson 2.15+ default)
- `SerializedQueryResponse` eliminated from transport path — ~40% peak heap reduction for large responses
- `PgDistributedLock`: null-DataSource falls back to JVM-only semaphore (safe for single-JVM ITs)

### Changed

- Toolchain bumped: Java 21 → **25**, Gradle 8.5 → **9.0**, Spring Boot 3.2 → **3.5.5**,
  Jackson 2.15 → **2.18.2** (+ CBOR), Netty **4.1.118.Final**
- `HandlerMetadataBuilder`: extracted from `EventoBundle.start()` into a focused class
- `EventoBundle.Builder`: consumer engines path is now always via `ConsumerEngineConfig`; in-memory
  default via `ConsumerEngineConfig::inMemory`

---

## [1.15.5] — 2025-01-01

Last stable v1.x release. The v1 source history is preserved in the git log.

---

[Unreleased]: https://github.com/EventoFramework/evento-framework/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/EventoFramework/evento-framework/compare/v2.0.0-rc1...v2.0.0
[2.0.0-rc1]: https://github.com/EventoFramework/evento-framework/compare/v1.15.5...v2.0.0-rc1
[1.15.5]: https://github.com/EventoFramework/evento-framework/releases/tag/v1.15.5
