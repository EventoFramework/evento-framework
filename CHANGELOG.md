# Changelog

All notable changes to Evento Framework are documented here.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning: [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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

[Unreleased]: https://github.com/EventoFramework/evento-framework/compare/v2.0.0-rc1...HEAD
[2.0.0-rc1]: https://github.com/EventoFramework/evento-framework/compare/v1.15.5...v2.0.0-rc1
[1.15.5]: https://github.com/EventoFramework/evento-framework/releases/tag/v1.15.5
