# Evento multi-region cluster: readiness assessment and design inputs for evento-lab-cluster

Synthesized from 6 read-only research reports. I re-checked the conflicting claims against the code:

- `PgDistributedLock.java` is 222 lines. The correct line refs are `:18` (the one pinned `lockCon`), `:20` (static semaphore map), `:28-31` (reconnect on `isValid`), `:80` (lock) and `:147` (unlock). One reader's `:196`/`:257` refs are wrong.
- `DomainCommand.getLockId()` returns `getAggregateId()` (`DomainCommand.java:40-42`). So aggregate commands **are** locked per aggregate by default. Only `ServiceCommand` returns `null` (`ServiceCommand.java:12-14`).
- Shipped `evento.es.mode=CPES` (`application.properties:51`). The code default is APES (`EventStore.java:73`).
- All Spring modules use Spring Boot plugin **4.1.0**, not the 3.5.5 that CLAUDE.md states.

---

## 1. Cluster readiness: can Evento run 3 active servers today?

**Only as three independent cells that share the database. It is not a routed cluster.**

Each evento-server is a standalone broker:

- Routing state is in-JVM only: `ClusterRegistry.java:35`, `ConnectionRegistry.java:155-156`, `ForwardingTable`, `ForwardingDedupCache` (built per lifecycle at `BusLifecycle.java:162`) and `reconnectBuffer` (`BusLifecycle.java:111-114`).
- There is no peer discovery and no server-to-server link.
- A request whose handler is not connected to the *same* server fails immediately with `no handler for <PayloadType>` (`BusLifecycle.java:477-482`).
- A bundle dials only `getAddresses().getFirst()` (`EventoBundle.java:607`) and reconnects to that same host:port forever (`ConnectionSupervisor.java:216-219`).

What the servers do share correctly through Postgres:

- **Event store.** Any server answers `EventFetchRequest` and `EventLastSequenceNumberRequest` from `es__events` (`EventStoreRequestHandlerBridge.java:44-89`). Context filtering happens in SQL (`EventStore.java:174-198`), so consumers see events written by any server.
- **Per-aggregate mutual exclusion.** `pg_advisory_lock(hashtext('RESOURCE:'+aggregateId))` covers `DomainCommand`s (`CommandBrokerHandler.java:107`), as long as every server's lock session is on the same live primary.
- **Global commit order in CPES.** The `es-lock` advisory lock plus `nextval` (`EventStore.java:234-254`) gives sequence order equal to commit order, as long as the lock sessions survive.
- **Catalog tables.** `core__*` rows are shared, but they are maintained racily (G7, G8).

### Topologies

| Topology | Works today? | Why |
|---|---|---|
| **Cell architecture**: each server hosts a *complete* set of handler bundles (command, service, query) for the bundles pinned to it; consumers anywhere | **Yes** | Every request resolves locally. Cross-cell consistency comes from the shared ES, the per-aggregate advisory lock and the CPES global order. |
| Region bundles pinned to their macro-region server, with handlers in only one cell | **No** | Other cells get `no handler for` (`BusLifecycle.java:481`). |
| Handler bundle registered on all 3 servers | **No** | One TCP session per bundle JVM (`EventoBundle.java:607`). The workaround is to run one replica per cell. |
| Bundles behind an L4 LB VIP (`nginx stream`, primary = local server, `backup` = others) | **Partially** | Failover lands on a server with empty registries. It works only if the target cell has full handlers. In-flight requests time out after 30 s (G4). `expectedServerInstanceId` must stay null (`ConnectionSupervisor.java:269-277`). |
| Bundle config listing 3 addresses for failover | **No** | Addresses 2 and 3 are silently ignored (`EventoBundle.java:607`). |
| Pure event consumers (projectors, observers) on any server | **Yes** | Fetch is served from the shared DB. Replicas with the same bundleId+context compete via `JdbcConsumerLock`. |
| Server datasource on a hot standby or behind pgbouncer (transaction mode) | **No** | Writes and advisory locks fail on a standby. Session advisory locks are meaningless behind a transaction pooler. Use a multi-host URL with `targetServerType=primary`. |
| APES mode with 3 servers | **Unsafe** | Snowflake ids come from 3 clocks and MAC-derived node ids. The 69 ms read window (`EventStore.java:71,181-184`) is smaller than WAN commit latency, so events are skipped. `recordAbsent` explodes (G10). **Use CPES.** |
| Cold start of 3 servers at once | **Flaky** | Every server runs `schema.sql` concurrently (`spring.sql.init.mode=always`, `application.properties:32`), which risks `pg_type_typname_nsp_index` or `tuple concurrently updated` errors. Stagger startup with `depends_on: service_healthy` chains. |
| Three servers with default identity | **Misleading** | All announce `iris-evento-server` (`application.properties:7,44`), and all use `evento.server.instance.id=server-0` (`:37`). Set unique `EVENTO_CLUSTER_NODE_SERVER_ID` and `EVENTO_SERVER_INSTANCE_ID` per server. |

The honest target for the lab is **3 active cells (EU, AM, APAC)**. Each cell has a full handler set, region JVMs attach to their cell's server, all share the event-store PG cluster, and cross-cell consistency rests on Postgres. The lab should demonstrate where this breaks.

---

## 2. Gap list (deduplicated, severity-ranked)

**IMPL** means worth implementing in this effort: small, safe, and testable over real TCP or a real PG. **DOC** means document it, or it is too large for this effort.

### Status after fixes

State of branch `feat/evento-lab-cluster` after the batch-1 merges (`cluster/consumer`,
`cluster/es-locks`, `cluster/discovery`, `cluster/client-failover`, `cluster/locality`) and
the batch-2 commits (N1, context wildcard, N3). "Fixed" means the IMPL item described below is
in the code with a real-TCP or real-Postgres test. The gap descriptions below are the original
assessment and are kept as written.

**End-to-end confirmation (2026-09-29).** All 18 scenarios S00..S17 ran on both `:before` (2.4.6)
and `:after`; the measured status per gap (including the lab findings N4 and N11..N17, which this
table does not list) is in
[`evento-lab-cluster/results/COMPARISON.md`](../evento-lab-cluster/results/COMPARISON.md), §"Per
gap". Where that table and this one disagree, the measured one wins: G10 is fixed in code but not
measurable by the lab (CPES), G5 is worse in practice after G4 (S14: 8 keys stored twice), and
failback after a G2 failover was missing (N16; implemented afterwards on `cluster/failback`, see
the N16 row, pending a lab re-run of S03/S13).

| Gap | Severity | Status after fixes | Evidence / what is left |
|---|---|---|---|
| G1 inter-server routing | Blocker | **Open** (DOC) | Cell architecture remains the workaround; no peer mesh |
| G2 first address only, no failover | Blocker | **Fixed** | Ordered endpoint list; next endpoint after a failed session, back to the first at the next disconnect, and by fail-back once the first is healthy again (N16). `BundleClientFailoverIT`, `BundleClientFailbackIT` |
| G3 lock pins one connection | Blocker | **Fixed** | Each held lock owns a connection from a dedicated pool (`evento.lock.pool.*`); polling waits bounded by `evento.lock.acquire.timeout.ms` or the caller's deadline; `LockLostException`. `PgDistributedLockIT`, `DistributedLockPoolPostgresIT` |
| G4 in-flight requests on broker change | High | **Fixed** | `BrokerChangedException` when a new `Welcome` names another broker. `BundleCorrelationTrackerBrokerChangeTest`, `BundleClientFailoverIT` |
| G5 no exactly-once across servers | High | **Open**, narrowed | N3 stops executing commands whose caller has given up; an append that outlives its deadline is still indeterminate. Idempotency key not implemented |
| G6 no `es__events` PK / no append fence | High | **Partly fixed** | PK added online on boot (`SchemaInitializationPostgresIT`); `verifyHeld` before append narrows the fork window; the `aggregate_seq` fence is open |
| G7 stale `NodeLeft` wipes a failed-over bundle | High | **Fixed** | Presence table `core__bundle_instance` (`BundlePresence`). `MultiServerCatalogIT` |
| G8 discovery / registration insert race | High | **Fixed** | One `DISCOVERY:<bundle>` key, `ON CONFLICT` inserts, one transaction per pass, retried on 40P01/40001/23503. `MultiServerCatalogIT` |
| G9 CPES `es-lock` ~4 round trips | High | **Fixed** | Single statement (`pg_advisory_xact_lock` CTE + `INSERT ... RETURNING`), ~1.1 RTT through a 100 ms proxy. `EventStorePostgresIT` |
| G10 `recordAbsent` O(gap) | High | **Fixed** | `SequenceRangeSet`. `WatermarkCheckpointTest`, `SequenceRangeSetTest` |
| G11 checkpoint can go backwards | High | **Fixed** (monotonic guard) | `GREATEST(last_sequence, ?)`; no commit on drift when the store is ahead. Owner-epoch fence not done |
| G12 no locality in handler pick | High | **Fixed** (zone) | `LOCALITY_PREFERRED` + `zone:<Z>` capability. `LocalityPickIT`. Routing by event context remains open (G23) |
| G13 server identity | Medium | **Fixed** | `Welcome.clusterName`, `withExpectedClusterName`; unique server ids are still the operator's job |
| G14 concurrent `schema.sql` | Medium | **Fixed** | Schema init under an advisory lock. `SchemaInitializationPostgresIT` |
| G15 unescaped LIKE | Medium | **Fixed** | `_`/`%` escaped, `ESCAPE '!'`; the literal context `"ALL"` is pinned too. `EventStorePostgresIT` |
| G16 consumerId split on `_` | Medium | **Fixed** | `ConsumerIds.parse`. `ConsumerIdsTest` |
| G17 views and admin are local | Medium | **Partly fixed** | Presence table gives cluster-wide ownership; dashboard and consumer admin still act on the local view |
| G18 discovery on the bus thread | Medium | **Open** (DOC) | |
| G19 `ExpiringLruCache` | Medium | **Fixed** | Synchronised, TTL-aware reads. `ExpiringLruCacheTest`. Cross-server invalidation after `deleteAggregate` open |
| G20 Snowflake node id from MAC | Medium | **Open** (DOC) | Use CPES |
| G21 no read datasource | Medium | **Open** (DOC) | |
| G22 transport settings not in the builder | Medium | **Fixed** | `setTransportConfig`, `setDefaultRequestTimeout`, `setMaxInFlightRequests`, heartbeat overrides |
| G23 commands carry no context | Medium | **Documented** | `DomainCommand` / `setComponentContexts` javadoc; by design |
| G24 same bundle+context replicas compete worldwide | Medium | **Documented** | `setComponentContexts` javadoc and ARCHITECTURE §4.8: one engine and lane per context. Lane affinity (N4) open |
| G25 orphaned consumer lock after a silent partition | Medium | **Open** (DOC) | PG keepalives in the lab |
| Low: telemetry sweep on every server | Low | **Fixed** | `tryLockedArea` elects one server |
| Low: `@Projector(context=...)` javadoc | Low | **Fixed** | Examples no longer show the removed attribute |
| Low: contexts keyed by simple class name | Low | **Documented** | `setComponentContexts` javadoc |
| N1 components keyed by class name across bundles | High | **Fixed** | `core__component.id = bundle:name`, in-place migration, unregister scoped to the bundle; same bundle id with different contexts per instance keeps every lane. `MultiServerCatalogIT`, `SchemaInitializationPostgresIT` |
| N2 liveness probes on the command path | Medium | **Partly fixed** | The lock validates only after an I/O error; Hikari's borrow check remains, and `verifyHeld` adds one `pg_locks` query per locked append |
| N3 commands executed after the caller timed out | High | **Fixed** (up to the append) | Deadline = arrival + `Request.timeoutMillis`; drops at `queued`, `lock_wait`, `before_forward`, `before_append`; `RequestExpiredException`; `evento.server.requests.expired{stage}`. `CommandDeadlineIT` |
| N4 global lane placed without locality | Medium | **Open** | Place the single global consumer in the primary's cell, or add lane affinity |
| N5 `es-lock` unfair to remote cells | Medium | **Partly fixed** | G9 cuts the hold to one statement; remote cells still pay their RTT per append |
| N16 no fail-back after a G2 failover | High | **Fixed** (not yet re-measured in the lab) | Bundle-side probe of endpoint 0 (TCP + transport `Ping`, never `Hello`: nothing registered on the probed broker), stability window 30 s + jitter ≤ 10 s (+ one window for bundles without handlers), graceful switch: `evento:disable`, outbound gate, bounded drain, then endpoint 0; leftovers get `BrokerChangedException`; failed switch doubles the next window. No wire change. `BundleClientFailbackIT`; S03/S13 now observe it. Residual: per-bundle, not per-cell, so a cell is briefly split (G1 `no handler for`); no fail-back behind a single-address VIP |

**Contexts, `"*"` versus `"ALL"`.** `com.evento.common.utils.Context.ALL` is the wildcard `"*"`,
not a context named `"ALL"`. It is now deprecated in favour of `Context.WILDCARD`. Applications that
publish cross-region events under the literal context `"ALL"` must declare their own constant
(e.g. `Contexts.GLOBAL = "ALL"`); a consumer configured with `"ALL"` reads only those events.

### Blockers

**G1: No inter-server routing (islands).** DOC for this effort; the design is recorded.
- Evidence: `BusLifecycle.java:477-482`, `ClusterRegistry.java:35` (in-memory `Map<String,Set<NodeAddress>>`).
- Impact: a cross-cell request fails with `no handler for`.
- Fix proposal: a server peer mesh. Each server dials its peers as a bundle, advertises local handlers via `BundleRegistrationInfo`, and on a local miss forwards through the existing zero-copy `ForwardingTable` with a hop marker to prevent loops. This is large. The workaround in this effort is the cell architecture.

**G2: Bundle uses only the first server address; no failover.** IMPL.
- Evidence: `EventoBundle.java:607`, `BundleClientConfig.java:31-32` (single host/port), `ConnectionSupervisor.java:216-219`.
- Impact: killing a server strands its bundles, although the other 2 servers are healthy.
- Minimal fix:
  - `BundleClientConfig` carries `List<Endpoint>`.
  - `ConnectionSupervisor.runOneSession` rotates to the next endpoint after a failed session, in ordered priority (local first).
  - It periodically re-tries index 0 to fail back.
  - `EventoBundle` passes all addresses.
  - Log `endpoint=` on `event=handshake_complete`.
  - Test: 2 real `NettyServerTransport`s, kill the first, and assert READY on the second.

**G3: `PgDistributedLock` pins one JDBC connection per instance.** IMPL.
- Evidence: `PgDistributedLock.java:18,28-31,80,147`. There are 5 instances per server: `EventStore:78`, `CommandBrokerHandler:82`, `AutoDiscovery:60`, `ConsumerService:63`, `PerformanceStore:72`.
- Impact 1: a thread blocked in `pg_advisory_lock(X)`, waiting on another server, holds the only connection. All other acquires and *releases* on that server queue behind it, so two servers can deadlock each other until `socketTimeout=30` (`application.properties:25`) kills the connection.
- Impact 2: when the connection dies (failover, timeout, partition), Postgres silently drops every lock held by that session while the JVM semaphore still says "held". Mutual exclusion is lost, and in CPES the `es-lock` too.
- Minimal fix:
  - Each held lock owns its own connection: borrow on acquire, unlock and close on release.
  - Use a bounded wait (`SET lock_timeout` or a `pg_try_advisory_lock` + backoff loop).
  - Verify the connection is still valid before leaving the critical section, and throw `LockLostException` if it is not.
  - Test: 2 lock instances on a real PG, cross-key contention, assert no head-of-line blocking.

### High

**G4: In-flight requests are not failed on disconnect; replies are lost when the bundle lands on another server.** IMPL.
- Evidence: `BundleCorrelationTracker.java:95,145` (`failAll` runs only on shutdown). The reconnect buffer is per server and replayed only on Hello to the same server (`BusLifecycle.java:111-114,390-391,610-624`).
- Impact: after a server kill, every in-flight call hangs for 30 s and then raises `RequestTimeoutException` with an indeterminate outcome.
- Minimal fix: remember the `serverInstanceId` of the session each request was sent on. When a new session's Welcome announces a *different* id, fail those futures immediately with a typed `BrokerChangedException` (indeterminate). Keep the survive-reconnect behaviour when it is the same server. This depends on unique server ids (G13).

**G5: No cross-server exactly-once; retries double-apply.** DOC now; possibly IMPL later.
- Evidence:
  - `BundleClient.java:146` mints a new UUID per call.
  - `EventoServerAdapter.java:95` mints another inside the envelope.
  - `BusLifecycle.java:297` mints a new UUID per server-side forward.
  - `ForwardingDedupCache` is in-memory (50k entries, 5 min).
  - The code itself acknowledges the problem at `EventoServerAdapter.java:121-131`.
- Impact: a retry after a timeout or failover appends duplicate events.
- Fix proposal: a caller-supplied idempotency key persisted in the same statement as the event (a nullable `es__events.idempotency_key` with a UNIQUE index). On conflict, return the stored event.

**G6: No DB-level fencing of aggregate appends; `es__events` has no PK.** IMPL (the PK part).
- Evidence: `schema.sql:92-101` has no PK, and the indexes at `:196-200` are non-unique. The `@Id` exists only in JPA (`EventStoreEntry.java:26-27`). The INSERT at `EventStore.java:217-256` has no version check.
- Impact: when a lock is lost (G3), two servers append from the same story and the aggregate forks. In APES, Snowflake node-id collisions produce duplicate sequence numbers.
- Minimal fix:
  - Add an idempotent `ALTER TABLE es__events ADD PRIMARY KEY (event_sequence_number)` in a `DO` block that checks `pg_constraint` first.
  - Later: add an `aggregate_seq` column with `UNIQUE(aggregate_id, aggregate_seq)` for optimistic concurrency. That would also remove the per-command WAN lock round-trips.

**G7: `NodeLeft` on the old server wipes a bundle that failed over to another server.** IMPL.
- Evidence: `AutoDiscoveryService.java:317` (`clearInstance`) and `:324-331` (`bundleStillLive` checks only the local `currentView()`, see `BusLifecycle.java:263`).
- Impact: instance I reconnects to server B. About 45 s later server A detects the dead socket, deletes I's fresh `core__consumer` rows and unregisters the whole bundle catalog cluster-wide.
- Minimal fix: record the owning server when a bundle joins (`core__bundle.server_instance_id`, or an instance table), then on leave skip `clearInstance` and `unregister` if another server owns the row.
- Test: 2 real servers on one PG. Move a bundle from A to B, drop A's socket, and assert the catalog is intact.

**G8: Discovery and consumer registration race on inserts.** IMPL.
- Evidence: the two paths use different lock keys, `"DISCOVERY:"+bundleId` (`AutoDiscoveryService.java:65`) and `"CONSUMER_DISCOVERY:"+instanceId` (`ConsumerService.java:83`). Both find-then-insert the bundle row (`AutoDiscoveryService.java:69-71`, `ConsumerService.java:135-137`). Shared payload rows are created under per-bundle keys. There is no `@Transactional`.
- Impact: `duplicate key` errors on `core__bundle_pkey`, `core__component_pkey` or `core__payload_pkey` abort the pass and leave the catalog half-written. This happens frequently on a mass reconnect after a server kill.
- Minimal fix: native `INSERT ... ON CONFLICT DO NOTHING` for bundle, component and payload, or have `registerConsumers` take the same `DISCOVERY:` key.

**G9: CPES global `es-lock` costs about 4 WAN round trips per event.** IMPL (optional, measurable).
- Evidence: `EventStore.java:44` and `:234-254`. The lock, `nextval`, the INSERT and the unlock run as separate autocommit statements on 2 different connections.
- Impact: at 150 ms RTT, cluster-wide append throughput is about 2 events/s.
- Minimal fix: a single transaction on one connection: `pg_advisory_xact_lock(const)` + `INSERT ... VALUES (nextval(...), ...) RETURNING event_sequence_number`. That is one round trip plus the commit. The lock is released at commit and cannot leak past a dead session, which also closes the `es-lock` half of G3.

**G10: WATERMARK `recordAbsent` is O(numeric gap).** IMPL.
- Evidence: `ConsumerProcessor.java:819-821` loops over every integer from `dispatchedSeq+1` to `top` into `completedAboveWatermark` (a TreeSet).
- Impact: in CPES, a per-context consumer (1 of 7 regions) adds about 6/7 of the global numbers to the set each batch, which costs CPU and churn. In APES the loop spans about 4.2 million ids per millisecond, which hangs the consumer or exhausts memory. A late lower commit after a lost lock is silently marked as "burned".
- Minimal fix: close the whole `[dispatchedSeq+1, top]` interval in one step. Store completed ranges, or advance directly when every present seq in the batch is below the first in-flight one. Test with a context-filtered consumer over a real server + PG.

**G11: Consumer checkpoint commit is not fenced and can go backwards.** IMPL (the monotonic guard).
- Evidence: `ConsumerProcessor.java:982-997` re-reads the version on `OptimisticLockException` and commits anyway. `JdbcConsumerLock.java:57-68` never re-validates the lock. Commits use other pool connections (`JdbcConsumerStateStore.java:192-194`).
- Impact: after a partition or PG failover, 2 replicas process the same lane and the stale one can rewind the checkpoint.
- Minimal fix: make the commit `SET last_sequence = GREATEST(last_sequence, ?)` (never lower). Better, an `owner_epoch` that is incremented on acquire and checked in the `WHERE` clause.

**G12: Handler pick has no locality or context.** DOC, or IMPL as a stretch.
- Evidence: `ClusterRegistry.java:32` (`enum PickStrategy { RANDOM, FIRST }`) with RANDOM as the default at `:107`. `NodeAddress` is `(bundleId, bundleVersion, instanceId)`. `Request` and `Hello` have no region or context field. A grep for region/zone/locality finds nothing.
- Impact: inside a cell that holds several regions, a CINA command can go to an AUSTRALIA_NZ replica. A query can land on a replica whose read model does not hold that context.
- Fix proposal: carry `zone:<X>` in Hello capabilities (no new wire field) and add `PickStrategy.LOCALITY_PREFERRED` (same zone as the originator, then any), configurable in `BusProperties`. A context routing key on `Request` is a larger change. In the cell architecture most of this is avoided by design.

### Medium

**G13: Server identity.** IMPL.
- Evidence: `application.properties:7,44` (`iris-evento-server` everywhere) and `:37` (`server-0`). The UUID fallback in `BusProperties.java:37-39` never fires. `expectedServerInstanceId` is a single string (`ConnectionSupervisor.java:269-277`), so a bundle behind an LB or with an address list cannot keep the 2.4.6 wrong-broker guard.
- Fix: add `clusterName` to the handling (either `Welcome` or a capability) and an `expectedClusterName` option on the bundle, or accept a set of ids / a per-endpoint expected id. Document that unique per-server ids are required.

**G14: Concurrent `schema.sql` on every server start.** IMPL.
- Evidence: `application.properties:32`, `schema.sql:181-194` (ALTER ... IF [NOT] EXISTS takes ACCESS EXCLUSIVE locks), `:205` (`create or replace view`), `:227` (sequence). Flyway is test-only (`evento-server/build.gradle:44`).
- Fix: wrap the initializer in `pg_advisory_lock(<const>)`. For example, a `DataSourceScriptDatabaseInitializer` subclass that locks and unlocks on one connection, or a leading/trailing lock statement in the script with the same connection guaranteed.

**G15: Context matching uses LIKE with no escaping.** IMPL.
- Evidence: `EventStore.java:178,182,191,196` does `context.replace("*","%")` and passes the result to `like ?1` (`EventStoreRepository.java:13-29`).
- Impact: `SUD_AMERICA` and `AUSTRALIA_NZ` contain `_`, which LIKE treats as a one-character wildcard.
- Fix: escape `\`, `%` and `_` before turning `*` into `%`, and add `ESCAPE '\'`. Or use `=` when the context has no `*`.

**G16: The admin UI parses consumerId by splitting on `_`.** IMPL.
- Evidence: `ConsumerController.java:49-50` uses `split("_")[3]` and `[2]`. The consumerId format is `bundleId_Component_version_context` (`ProjectorEngine.java:171`).
- Impact: `SUD_AMERICA` is shown as `SUD`.
- Fix: parse the components structurally, e.g. context = everything after the third `_` when the bundleId has no `_`. Better: persist the structured fields at registration time.

**G17: Cluster views, dashboard and consumer admin use only the local view.** DOC.
- Evidence: `DashboardController.java:52-57`; `ConsumerController.java:36-55`; `ConsumerService.java:174-176,231-233,269-271,314-316` (`orElseThrow`); `BusLifecycle.java:292-295` (`no connection for`).
- Fix proposal: a presence table with a heartbeat, and proxying admin calls to the owning server.

**G18: Discovery runs synchronously on the bus thread.** DOC.
- Evidence: `BusFacade` javadoc, `AutoDiscoveryService.java:52-58`, `BundleService.java:52,78` (full-table scans).
- Impact: with a WAN primary, a mass reconnect stalls the bus for seconds.

**G19: `ExpiringLruCache` is not thread-safe and its TTL is bypassed.** IMPL.
- Evidence: `ExpiringLruCache.java:6` extends an access-ordered `LinkedHashMap` with no synchronization. `EventStore.java:313` (`containsKey`) and `:317` (`getOrDefault`) bypass the TTL. `deleteAggregate` invalidates only the local cache (`EventStore.java:282-295`).
- Fix: synchronize or switch to `ConcurrentHashMap`, and route every read through the TTL-aware `get`.

**G20: Snowflake node id is derived from the MAC address and is not configurable.** DOC. It matters only for APES, and CPES is recommended.
- Evidence: `EventStore.java:51` (`new Snowflake()`), `Snowflake.java:115-140`.

**G21: No separate read datasource or replica awareness.** DOC.
- Evidence: a single `spring.datasource` (`application.properties:41-43`).
- Note: in CPES a replica holds a commit-ordered prefix, so pointing only fetches at it would be safe. That is a future `evento.es.read.datasource.*`.

**G22: Transport, heartbeat and max-in-flight settings are not reachable from `EventoBundle.Builder`.** IMPL (small).
- Evidence: `EventoBundle.java:620-639`; `BundleClient.java:250-268`; `NettyTransportConfig.java:83-95` (read-idle 45 s).
- Impact: a silent partition is detected only after 45 s.
- Fix: expose `setTransportConfig` / `setReconnectStrategy` / `setDefaultRequestTimeout`.

**G23: Commands carry no framework-level context.** DOC.
- Evidence: `Event.java:13` has a context field; Command, DomainCommand and TrackablePayload do not. The stale javadoc reference is at `DomainCommand.java:24-29`.
- Workaround: copy it by hand, as `OrderAggregate.java:21` does.

**G24: Replicas with the same bundleId and context compete worldwide.** DOC. This is working as designed, but undocumented.
- Evidence: consumerId has no instance or region component (`ProjectorEngine.java:171`), and the lock is `lock.tryAcquire(consumerId)` (`ConsumerProcessor.java:155`).
- Impact: a regional active copy of a projector needs its own bundleId, e.g. `query-europa`.

**G25: Orphaned consumer advisory lock after a silent partition.** DOC. Tune Postgres keepalives in the lab.
- Evidence: `JdbcConsumerLock.java:12-25,104-126`. The lock is held for the whole cycle, including the 30 s fetch.
- Lab settings: `tcp_keepalives_idle=10`, `tcp_keepalives_interval=5`, `tcp_keepalives_count=3`, `client_connection_check_interval=5s`.

### Low
All DOC.
- The hourly telemetry cleanup runs on every server (`PerformanceStoreService.java:357-363`). Guard it with `tryLockedArea`.
- The SSE `/view` endpoint leaks a bus subscriber per open (there is no unsubscribe API).
- The fetch staleness gate compares client and server wall clocks (`EventStoreRequestHandlerBridge.java:57-63`).
- If the first connect fails, startup fails but the supervisor keeps running (`ConnectionSupervisor.java:186-188`, `EventoBundle.java:645`).
- `forwardOrRetry` uses a fresh correlationId per attempt (`CommandBrokerHandler.java:241-257`).
- Saga state has no context dimension (`SagaStateStore.java:25,33`).
- The `@Projector(context=...)` javadoc shows a removed attribute (`Projector.java:22`, `Saga.java:19`, `Observer.java:22`).
- Contexts are keyed by simple class name (`EventoBundle.java:344,365`).

### Lab and packaging defects
Fix these inside `evento-lab-cluster` without touching the core.
- **L1:** the `COPY evento-server/build/libs/*.jar` glob in `Dockerfile.local:7` picks up 6 stale `*-plain.jar` files. The lab should use its own Dockerfile with explicit jar names, and/or set `jar { enabled=false }`.
- **L2:** the ms query, saga and observer apps set `evento.server.port=3000`, which is the HTTP port. It must be **3030**.
- **L3:** the ms apps accept one address. Add an `EVENTO_SERVER_ADDRESSES` list.
- **L4:** there is no HTTP ingress (starter only). Add a region gateway with `starter-web` + actuator.
- **L5:** `ConsumerEngineConfig::inMemory` (`ms-query EventoConfiguration.java:36-37`) plus a static `OrderViewStore` make replicas double-process. Use the JDBC state store and JDBC read models.
- **L6:** `MsMultiContextIT` does not assert isolation, and its fake store's semantics differ from the real LIKE (`MsTestEventStore.java:50-56`).

### Recommended IMPL batch
In priority order: G2, G4, G13 (the minimum needed for failover to be meaningful), G3 + G9 (lock correctness and WAN throughput), G7, G8, G14, G10, G11, G15, G16, G19, and the PK part of G6. Every item is local and has a clear real-TCP or real-PG test.

---

## 3. Experiment design inputs

### Facts

- **Toolchain.** JDK 25 is at `C:/Users/Gaborando/.jdks/openjdk-25.0.1`. The default `JAVA_HOME` is 21.0.2, which is fine for the Gradle daemon, and the toolchain auto-detects 25. Gradle 9.6.1 wrapper. Offline builds work.
- **Build.**
  ```
  ./gradlew :evento-server:bootJar <lab modules>:bootJar -x test --offline
  ```
  `scripts/dev-local.sh` is macOS-only (`/usr/libexec/java_home`), so ship both `.sh` and `.ps1` scripts.
- **Image.** A generic `evento-lab-cluster/docker/Dockerfile`:
  - `FROM eclipse-temurin:25-jre` (not cached locally; it needs one pull).
  - `ARG JAR`, then `COPY` of the explicit jar.
  - `ENTRYPOINT java $JAVA_OPTS -jar /app/app.jar`.
  - Do not reuse `Dockerfile.local` (L1), or the full `Dockerfile`, which rebuilds the repo inside the container.
- **Server ports.** HTTP/GUI on 3000 (Basic auth `evento/secret`), bus on 3030. Health check: `GET :3000/actuator/health/readiness`. Prometheus endpoint at `/actuator/prometheus` (`application.properties:4-6,54-58`).
- **Server env per instance:**
  - `SPRING_DATASOURCE_URL=jdbc:postgresql://es-pg-eu:5432,es-pg-am:5432,es-pg-apac:5432/evento?targetServerType=primary&tcpKeepAlive=true`
  - `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
  - `EVENTO_CLUSTER_NODE_SERVER_ID=evento-eu|evento-am|evento-apac` (this becomes the Welcome `serverInstanceId`)
  - `EVENTO_SERVER_INSTANCE_ID=` the same value
  - `EVENTO_CLUSTER_NAME=evento-lab-cluster`
  - `EVENTO_ES_MODE=CPES` (mandatory)
  - optional: `EVENTO_SERVER_BUS_AUTH_TOKEN`, `EVENTO_SERVER_BUS_HEARTBEAT_READ_IDLE` (lower it to 10s for faster detection)
  - Hikari defaults: `socketTimeout=30`, `connectTimeout=30`.
  - Hikari pool must be at least 10 + 5, because 5 connections are pinned by the `PgDistributedLock` instances (G3).
- **Server tunables:**
  - `evento.es.fetch.concurrency` (4)
  - `evento.es.fetch.max.age.ms` (30000)
  - `evento.es.aggregate.{state,events}.cache.{size,expiry}` (1024 / 150000)
  - `evento.es.fetch.delay` (69; APES only)
- **Bundle wiring:**
  - `EventoBundle.Builder.setBundleId/setInstanceId/setBundleVersion/setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(new ClusterNodeAddress(h,p)...))`
  - `setComponentContexts(Projector.class, "CINA")` gives consumerId `<bundleId>_<Simple>_<ver>_CINA`
  - `setConsumerEngineConfigBuilder(...)` with JDBC:
    - `JdbcConsumerLock`, `JdbcConsumerStateStore`, `JdbcSagaStateStore`, `JdbcDeadEventQueue`, `JdbcDedupeStore`
    - `FlywayMigrator.migrate(ds, SqlDialect.POSTGRES)`
    - pattern: `evento-lab/src/test/java/com/evento/lab/consumer/AbstractJdbcConsumerIT.java:67-114`
  - The consumer pool must exceed the number of concurrent engines, because each holds a pinned lock connection for its cycle.
  - Several `EventoBundle` instances can live in one JVM; `MsHarness` already does this.
- **Context modeling:**
  - Put `region` on the command and have the handler return `new XEvent(...).setContext(cmd.getRegion())` (pattern at `OrderAggregate.java:21`).
  - Regional projector: bundleId `query-<region>` + `setComponentContexts(..., REGION)`.
  - Global projector: no contexts set, which means `*`.
  - Because of G15 and G16, test the underscore names (`SUD_AMERICA`, `AUSTRALIA_NZ`) explicitly. They are a regression target for those fixes.
- **Domain.** Reuse the `evento-lab-ms-api` Order domain: Create/AddItem/Complete, the payment saga, notifications. `CreateOrderCommand` already has `context`.
- **Docker host.** 8 CPU, 15.59 GiB, WSL2 cgroup v2, Docker 29.6.1, compose v5.3.0.
  - Cached images: `postgres:17`, `postgres:17-alpine`, `nginx:alpine`.
  - Not cached: toxiproxy (`ghcr.io/shopify/toxiproxy`, one pull), temurin 25 JRE, patroni.
  - Do failover manually: `SELECT pg_promote()` plus a multi-host JDBC URL.
- **Latency emulation.**
  - Toxiproxy per WAN link: server to ES primary, region JVM to server, consumer to app-DB primary. It is controllable over its HTTP API from the chaos driver.
  - Alternative: `tc netem` needs `NET_ADMIN` and iproute2, which alpine postgres lacks.
- **PG replication.** `postgres:17-alpine` with the primary using `wal_level=replica` and `max_wal_senders`. Replicas are initialised with `pg_basebackup -R`, async by default. Keep one replica on `synchronous_standby_names` to contrast async and sync failover.
  - Keepalives: `tcp_keepalives_idle=10`, `tcp_keepalives_interval=5`, `tcp_keepalives_count=3`, `client_connection_check_interval=5s`.
  - Scratch SQL: `SELECT * FROM pg_locks WHERE locktype='advisory'`.
- **Log markers to assert on:**
  - `no handler for`, `no connection for`, `peer disconnected:`
  - `event=handshake_complete server_instance=`, `event=bundle_state_transition`, `event=supervisor_session_failed`, `event=bundle_correlation_expired`
  - `event=server_instance_mismatch`, `event=lock_release_failed`
  - `Skipping unregister of bundle`, `[BundleService] Unregister called for bundleId=`
  - `duplicate key` (`core__bundle_pkey` / `core__component_pkey` / `core__payload_pkey`)
- **Metrics to assert on:** `evento.server.forwarding.table.size`, `evento.server.forwarded{path}`.

### Recommended topology (fits 15.6 GiB with about 6 GiB headroom)

The 7 regions map to 3 cells, each with one evento-server (cell = macro-region):

| Cell | Server | Regions | ES-PG node | App-PG node |
|---|---|---|---|---|
| EU | `evento-eu` | EUROPA, AFRICA | **primary** | **primary** |
| AM | `evento-am` | AMERICA, SUD_AMERICA | replica (sync) | replica |
| APAC | `evento-apac` | CINA, INDIA, AUSTRALIA_NZ | replica (async) | replica |

JVMs, each running `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k` with `mem_limit` set:

- **3 × evento-server** at 512 MB each.
- **3 × `cell-core-<cell>`** at 384 MB. Each hosts the full handler set for its cell: bundle `cluster-command` (OrderAggregate + Services) and `cluster-query-global` (global `*` projector into app-PG, plus query handlers). The same bundleId in every cell means the global projector is one competing lane worldwide (G24), and command handlers are replicated once per cell.
- **7 × `region-<R>`** at 320 MB. Each hosts:
  - an HTTP gateway (POST /orders, GET /orders/{id}), which stamps `region=R` and sends through its cell's server;
  - bundle `query-<r>` with `setComponentContexts(RegionOrderProjector, R)` writing `order_view_<r>`, plus the query handler for its own region's view. Query payload types are per region, or the gateway queries its local bundle.
- **2 × `cluster-saga-observer`** at 320 MB, in the EU and APAC cells. Same bundleId, so they are competing consumers (tests lock handover). Each issues commands through its own cell's server, and that works because every cell has command handlers.
- **Optional `cell-core-eu-b`** second replica, only for scenario S2 (handler failover inside one cell).

Other containers:

- **6 × Postgres** at 256 MB: es-pg-{eu,am,apac} and app-pg-{eu,am,apac}.
- **1 × nginx:alpine** at 32 MB:
  - `http` block: route on `X-Region` header (or a `?region=` query param mapped via `map`) to `region-<R>:8080`, with the same-cell region upstreams as `backup` servers.
  - `stream` block on 3030 per cell: primary = local server, `backup` = the other two. This emulates DNS/VIP failover and works around G2 until it is fixed.
- **1 × toxiproxy** at 64 MB. WAN RTT matrix, e.g. EU↔AM 80 ms, EU↔APAC 150 ms, AM↔APAC 170 ms, with ±10 ms jitter.
- **1 × chaos driver**: a JUnit or plain-Java module run from the host via the docker CLI + HTTP + JDBC.

Total: about 16 JVMs, roughly 6.3 GB for JVMs, 1.5 GB for PG and 0.1 GB for LB/proxy, so about 8 GB.

Networks: one bridge per cell (`net-eu`, `net-am`, `net-apac`) plus a `wan` network. All cross-cell traffic goes through toxiproxy, which means `docker network disconnect` of a cell cleanly emulates a region partition.

Folder layout:

```
evento-lab-cluster/
  lab-api/ lab-support/           # shared messages + Contexts.GLOBAL="ALL"; deployment contract, stores
  order-command/ order-query/ order-saga/ order-observer/ order-gateway/
                                   # one bundle per responsibility; topology by env only (EVENTO_CONTEXTS, ...)
                                   # (as built: supersedes the original one-jar EVENTO_ROLE design)
  evento-lab-cluster-chaos/        # scenario tests + invariant checker
  docker/{Dockerfile,compose.yaml,pg/,nginx/,toxiproxy/}
  scenarios/S1..Sn/{run.sh,run.ps1,README.md}
  results/
```

Add the modules to `settings.gradle`, and set Spring Boot plugin 4.1.0 with `jar { enabled=false }`.

### Invariants the checker should verify after every scenario
- **I1 (no lost events):** each order accepted via HTTP 2xx has exactly its expected events in `es__events`.
- **I2 (no duplicate events):** no two events share (`aggregate_id`, logical step). Check the count of `OrderCreated` per `aggregate_id` equals 1.
- **I3 (no aggregate fork):** replaying each aggregate's events is valid. Sequence numbers are unique, which is also checked by the PK once G6 lands.
- **I4 (regional isolation):** `order_view_<r>` contains only context R.
- **I5 (global completeness):** the global view equals the union of all regions.
- **I6 (convergence):** checkpoints reach the ES head within T seconds after healing.
- **I7 (catalog):** after healing, the `core__bundle`, `core__handler` and `core__consumer` rows match the live bundles.
- **I8 (latency):** p99 command latency per region is recorded as a baseline. It is observed, not a hard pass/fail.

---

## 4. Chaos scenarios

Each scenario gives the setup, the fault injected, the expected invariant (what a correct system must show), and the gaps it exercises. For scenarios that expose a known gap, the "expected" column describes the behaviour before the fix, so the run documents the defect, followed by the target after the fix.

**S0: Baseline.**
- Setup: full topology, no faults, 7 regions × 20 orders/min through the LB.
- Fault: none.
- Expected: I1 to I7 hold, with per-region p50/p99 latency and ES append throughput measured.
- Exercises: the reference point for G9.

**S1: Cross-cell routing miss.**
- Setup: stop `cell-core-am`, so the AM cell has gateways but no handlers.
- Fault: AMERICA sends a command.
- Expected: fails with `no handler for CreateOrderCommand` although handlers are live in EU and APAC.
- Exercises: G1. Documents why the cell architecture is required.

**S2: Kill one handler replica inside a cell.**
- Setup: `cell-core-eu` + `cell-core-eu-b`.
- Fault: `docker kill cell-core-eu` under load.
- Expected: new commands go to eu-b (RANDOM pick among available nodes). In-flight commands fail with `peer disconnected`. I1 holds for acknowledged orders.
- Exercises: the baseline of intra-server failover.

**S3: Kill an evento-server.**
- Setup: `evento-apac` under load.
- Fault: `docker kill evento-apac`.
- Expected before the fixes:
  - APAC bundles reconnect through the nginx stream backup to `evento-eu` and re-register.
  - In-flight calls time out after 30 s (G4).
  - Retries may duplicate events (G5).
  - The discovery race produces `duplicate key` errors (G8).
- Expected after the fixes:
  - Using direct address lists, not the LB: G2 failover by address rotation.
  - Immediate `BrokerChangedException` (G4).
  - A clean catalog (G8).
  - I1, I4, I5 and I6 hold.
- Exercises: G2, G4, G5, G8, G13.

**S4: Server returns after failover.**
- Setup: S3.
- Fault: restart `evento-apac`. Separately, run the variant where the old server's socket half-closes (pause it with `docker pause` for longer than 45 s, then `docker unpause`).
- Expected: the old server's `NodeLeft` must NOT unregister the bundle that now lives on `evento-eu`. Check I7.
- Exercises: G7, G17.

**S5: Concurrent same-aggregate commands from two cells.**
- Setup: EUROPA and CINA gateways both send `AddOrderItem` to the same orderId in a tight loop, with WAN latency on.
- Fault: contention plus 150 ms RTT.
- Expected: no fork (I3), with the command latency distribution recorded. Before G3, expect p99 ≈ 30 s stalls and possible cross-server lock deadlock. Probe with `pg_locks`.
- Exercises: G3, G6, G9.

**S6: WAN latency sweep.**
- Setup: toxiproxy latency to the ES primary set to 0, 50, 150 and 300 ms.
- Fault: latency only.
- Expected: I1 to I6 hold. Record the throughput curve. Before G9, throughput is about 1/(4·RTT) events/s cluster-wide. After G9, about 1/(1–2·RTT).
- Exercises: G9, G18.

**S7: Region partition.**
- Setup: `docker network disconnect` of the APAC cell from the WAN for 60 s, then reconnect.
- Fault: silent partition (no RST).
- Expected:
  - APAC bundles detect the loss only after the 45 s read-idle timeout (G22).
  - Global consumer lanes held by an APAC JVM stay stuck until PG keepalive kills the backend (G25).
  - After healing: I6, and I2 (no duplicate projection side effects) for the global projector and the saga.
- Exercises: G22, G25, G11.

**S8: ES primary failover.**
- Setup: under load.
- Fault: `docker kill es-pg-eu`, then `pg_promote()` on es-pg-am (sync replica). Run a variant promoting es-pg-apac (async).
- Expected:
  - Servers follow via the multi-host URL.
  - Advisory locks vanish (G3). Check I3.
  - Ambiguous commits followed by client retries can duplicate (G5, check I2).
  - In the async variant, acknowledged events are lost and checkpoints can sit above the new head, so events are never delivered (check I1, I6). Record this as an operational limit.
- Exercises: G3, G5, G6, G19 (stale server caches).

**S9: App-data (consumer-state) primary failover.**
- Setup: under load.
- Fault: kill `app-pg-eu`, promote app-pg-am.
- Expected: consumer locks are lost and a second replica takes the lane. Checkpoints must never go backwards (G11); watch `last_sequence` over time. Replay is allowed, with idempotent projection (at-least-once).
- Exercises: G11, G25.

**S10: Cold start of 3 servers simultaneously on an empty DB.**
- Setup: `docker compose up` with the servers started together (no health chain).
- Fault: none beyond simultaneity; repeat 10 times.
- Expected: all 3 start. Before G14, expect occasional `pg_type_typname_nsp_index` / `tuple concurrently updated` boot failures.
- Exercises: G14.

**S11: Context isolation with underscore regions.**
- Setup: orders only in SUD_AMERICA and AUSTRALIA_NZ, plus a synthetic event with context `SUDXAMERICA`.
- Fault: none.
- Expected: `query-sud_america` must not receive `SUDXAMERICA` (I4), and the GUI must show the full context names.
- Exercises: G15, G16. It is also the real-server version of the missing `MsMultiContextIT` isolation assert (L6).

**S12: Per-context WATERMARK consumer cost.**
- Setup: the EUROPA-only projector in WATERMARK mode while the other 6 regions write 1k events/s.
- Fault: load.
- Expected: flat CPU and heap for the EUROPA consumer. Before G10, `completedAboveWatermark` churn grows with global volume. The APES variant is expected to hang or run out of memory (demonstration only).
- Exercises: G10, G20.

**S13: Wrong-broker and identity check.**
- Setup: bundles with `expectedServerInstanceId=evento-eu` behind the stream LB.
- Fault: kill `evento-eu`.
- Expected: without G13, the bundle rejects the healthy failover targets (`event=server_instance_mismatch` loop). With an expected cluster name, it accepts any `evento-lab-cluster` server and rejects a foreign one. To test the rejection, start a 4th server with a different cluster name.
- Exercises: G13.

**S14: Retry storm with idempotency.**
- Setup: the gateway retries a timed-out CreateOrder once, through another cell.
- Fault: kill the server mid-request.
- Expected: without G5, duplicate `OrderCreated` (I2 fails); this documents the gap. With an idempotency key, exactly one.
- Exercises: G5.

**S15: Clock skew (optional).**
- Setup: `libfaketime`, or run a server container with `TZ` skew (Docker cannot skew per container without faketime).
- Fault: +40 s on one region's JVM.
- Expected: the fetch staleness gate drops that region's fetches (`EventStoreRequestHandlerBridge.java:57-63`); document it.
- Exercises: the fetch-staleness Low item and G20 in APES.

### Suggested run order
S0, S10, S11, S1, S2, S3, S4, S5, S6, S12, S7, S8, S9, S13, S14.

Run them once before the IMPL batch to capture the defects in `results/<S>/before/`. Then run them again after each fix lands, to `results/<S>/after/`. The before/after pairs become the evidence for the thesis.