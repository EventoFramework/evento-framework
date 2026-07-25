# Changelog

All notable changes to Evento Framework are documented here.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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
