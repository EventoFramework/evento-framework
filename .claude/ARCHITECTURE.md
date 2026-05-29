# Evento Framework v2 — Architecture & Developer Reference

Single authoritative source for design decisions, module layout, wire protocol, key classes,
conventions, and test strategy. Read this before touching any module. Companion files:
- [`PLAN.md`](PLAN.md) — original v2 rewrite rationale and phased scope (historical; PRs 1–3 all shipped)
- [`STATUS.md`](STATUS.md) — session-level delta log; update at end of each session

---

## 1. What Evento Is

Evento is a Java framework for building distributed event-sourced / CQRS systems based on the
**RECQ** (Reactive Event-driven Commands & Queries) architecture pattern. The two runtime
components are:

| Component | Role |
|---|---|
| **Evento Server** | Central broker: bundle registry, event store, command routing, query routing, performance store |
| **Evento Bundle** | Application unit: hosts one or more domain components (`@Aggregate`, `@Projector`, `@Saga`, `@Service`, `@Observer`, `@Projection`, `@Invoker`); connects to the server over TCP |

Bundles register their handler types with the server at startup. All inter-bundle communication
(commands, queries, events) flows through the server broker; bundles never talk directly to each
other.

---

## 2. Repository Module Map

```
evento-framework/
├── evento-transport-api/         Wire SPI: sealed Message records, Codec, Transport, state machine, reconnect
├── evento-transport-netty/       Netty impl: CBOR pipeline, chunking, TLS, heartbeat, backpressure
├── evento-common/                Shared domain types: annotations, messaging types, consumer SPI
├── evento-bundle/                Bundle runtime: client, consumer engines, component managers
├── evento-server/                Server runtime: bus lifecycle, event store, REST API, Spring Boot app
├── evento-consumer-state-store/
│   └── evento-consumer-state-store-jdbc/  JDBC impls (Postgres + MySQL) for consumer SPIs
├── evento-parser/                Static analysis / codegen of handler metadata (server-side BundleDescription model)
├── evento-gui/                   Web GUI (React, static assets)
├── evento-lab/                   In-process integration tests (single-bundle RTT, failure matrix, etc.)
└── evento-lab-microservices/     Multi-bundle integration tests (RECQ microservices scenario)
    ├── evento-lab-ms-api/
    ├── evento-lab-ms-command/
    ├── evento-lab-ms-query/
    ├── evento-lab-ms-saga/
    ├── evento-lab-ms-observer/
    └── evento-lab-ms-it/
```

**Gradle**: version `2.0.0-rc1`, Java toolchain `25`, Spring Boot `3.5.5`, Jackson CBOR `2.18.2`,
Netty `4.1.118.Final`. Root `build.gradle` centralises all versions in `allprojects { ext { … } }`.

---

## 3. Wire Protocol v2

Every message on the TCP socket is:

```
[4 bytes length BE]         ← LengthFieldPrepender / LengthFieldBasedFrameDecoder
[envelope header]           ← CBOR-serialised Envelope record
[CBOR payload]
```

**Netty pipeline (both sides):**
```
EnvelopeFrameDecoder (LengthFieldBasedFrameDecoder, maxFrame 16 MB)
EnvelopeFrameEncoder (LengthFieldPrepender 4)
ChunkReassembler        ← inbound: reassembles CHUNK frames by stream UUID
ChunkingEncoder         ← outbound: FULL frame or 1..N CHUNK frames (no hard message size limit)
CborMessageDecoder      ← produces Frame(message, rawBytes)
CborMessageEncoder      ← encodes Message; passes ByteBuf through for sendRaw (zero-copy)
IdleStateHandler        ← triggers heartbeat on write idle
HeartbeatHandler        ← sends Ping; closes channel on reader idle
BackpressureHandler     ← transitions state → DEGRADED on high-water mark
MessageInboundHandler   ← dispatches Frame on virtual-thread executor
```

**Chunking:** `ChunkingEncoder` wraps every outbound `ByteBuf` as either:
- `FULL` frame (`0x00` + CBOR data, when it fits in one chunk)  
- `CHUNK` frames (`0x01` + 16-byte stream UUID + 1-byte isLast flag + data)

`ChunkReassembler` buffers chunks per stream UUID and fires downstream only on the last chunk.
The `maxFrameLength` parameter controls per-chunk memory, not message size — there is no hard limit
on message size.

**Sealed `Message` hierarchy** (`evento-transport-api`, `com.evento.transport.message`):
```
Message (sealed interface)
  ├── Hello      (bundleId, instanceId, bundleVersion, authToken, capabilities)
  ├── Welcome    (serverVersion, acceptedCapabilities)
  ├── Reject     (reason — code constants: AUTH_FAILED, VERSION_MISMATCH, etc.)
  ├── Ping       (seq, ts)
  ├── Pong       (seq, ts)
  ├── Request    (correlationId, payloadType: String, payload: byte[])
  ├── Response   (correlationId, payload: byte[], failure: ResponseError)
  └── Notification (payloadType: String, payload: byte[])
```

Adding a new wire type: extend `Message` permits + register a byte tag in `MessageTypeRegistry`.
The compiler enforces exhaustive dispatch everywhere.

**Protocol notifications** (Notification.payloadType constants in `ProtocolNotifications`):
- `evento:bundle-registration` — lean (bundleVersion + handlerPayloadTypes only)
- `evento:enable` — bundle is ready to receive messages
- `evento:disable`
- `evento:bundle-discovery` — rich metadata (handlers + payloadInfo schemas), sent post-enable

**Zero-copy forwarding:** The broker relays `Request`/`Response` via `Transport.sendRaw(byte[])`
using the raw bytes retained in `Frame`. No CBOR re-encoding on the broker hop.

---

## 4. Architecture Layers — Key Classes

### 4.1 Transport Layer (`evento-transport-api` + `evento-transport-netty`)

| Class | Where | Role |
|---|---|---|
| `Transport` | api | SPI: `send(Message)`, `sendRaw(byte[])`, `onFrame`, `onStateChange`, `close()` |
| `TransportServer` | api | Server SPI: `bind(port)`, `onChildTransport`, `stop()` |
| `Frame` | api | Parsed `Message` + retained raw wire bytes (zero-copy) |
| `ConnectionStateMachine` | api | `AtomicReference`-backed; legal transitions only |
| `ConnectionState` | api | `DISCONNECTED → CONNECTING → CONNECTED → DEGRADED → CLOSING → CLOSED`. `canSend()` is `true` for `CONNECTED` **and** `DEGRADED` (DEGRADED is advisory backpressure — socket is alive) |
| `ExponentialBackoffWithJitter` | api | `min(maxBackoff, base × 2^attempt) × (1 ± jitter)`; default base=500 ms, max=30 s, jitter=0.2 |
| `MessageTypeRegistry` | api | `byte tag ↔ Class<? extends Message>` whitelist |
| `JacksonCborCodec` | api | Default `Codec` impl (Jackson CBOR) |
| `InMemoryTransport` | api | Test double; `simulateDisconnect()` → DISCONNECTED (not DEGRADED) |
| `NettyClientTransport` | netty | Client-side Netty channel + reconnect |
| `NettyServerTransport` | netty | Server-side accept loop; each accepted channel → `ServerChildTransport` |
| `NettyTransportConfig` | netty | Optional `SslContext` (prepends `SslHandler` when set) |

### 4.2 Server Bus (`evento-server`, `com.evento.server.bus.*`)

| Class | Role |
|---|---|
| `BusLifecycle` | Orchestrator. `start(port)` / `stop(Duration)`. Owns handshake + routing + correlation + reconnect buffer |
| `BusFacade` | Spring SPI used by all server-side consumers (Dashboard, ClusterStatus, AutoDiscovery, Consumer, Bundle) |
| `BusLifecycleFacade` | `BusFacade` impl wrapping `BusLifecycle` |
| `BusFacadeConfiguration` | Unconditional Spring `@Configuration`; wires `BusLifecycleFacade` as the only path |
| `BusConfiguration` + `BusProperties` | Spring Boot auto-config + `evento.server.bus.*` properties |
| `ConnectionRegistry` | `ConcurrentHashMap<NodeAddress, (Connection, token)>`. `register` supersedes + closes old transport; `unregister` is token-guarded |
| `ClusterRegistry` | `payloadType → Set<NodeAddress>`. `RANDOM` / `FIRST` pick strategy |
| `CorrelationStore` | `ConcurrentHashMap<UUID, PendingCorrelation>`. Bounded shutdown; scheduler-driven expiry |
| `ForwardingTable` | `correlationId → (originatorAddress, destinationAddress)`. `drainByDestination(addr)` removes only destination-side entries (leaves originator-side alive for QoS reconnect delivery) |
| `ForwardingDedupCache` | LRU, 5 min TTL, 50k entries. Deduplicates retried `Request` at the broker |
| `HandshakeHandler` | Validates `Hello` → calls `TokenValidator` → calls `BusLifecycle.onHello` |
| `TokenValidator` | SPI: `acceptAll()` (default) or `sharedSecret(token)` (constant-time compare) |
| `MessageRouter` | OCP dispatcher — `Map<Class<? extends Message>, Handler>` registered in `start()` |
| `BundleSession` | Per-connection mutable state: `NodeAddress`, registered handlers, enable/disable flag |
| `BusEvent` | Sealed hierarchy: `BundleRegistered`, `BundleDiscovered`, `BundleLeft`, `AdminNotification`, …  |
| `BusEventBus` | `subscribe(Consumer<BusEvent>)` replaces v1's four parallel listener lists |

**Reconnect QoS (Fix D):** When a handler replies while the originator is disconnected,
`BusLifecycle.onResponse` buffers the `Response` in `reconnectBuffer` (keyed by `instanceId`,
2-minute TTL). `onHello` calls `deliverPendingResponses` on re-handshake.

**Supersede race (Fix A):** `onTransportDisconnected` returns early when `ConnectionRegistry.unregister`
returns empty (token mismatch = superseded session). This prevents wiping the handlers of a
reconnected bundle.

### 4.3 Bundle Client (`evento-bundle`, `com.evento.application.client.*`)

| Class | Role |
|---|---|
| `BundleClient` | Public façade: Builder, `start/stop`, `request(payloadType, byte[], Duration)`, `notify(...)`, `enable/disable`, `registerRequestHandler/NotificationHandler` |
| `BundleClientConfig` | Record + Builder: host/port list, identity, authToken, capabilities, timeouts, transport config, autoEnable |
| `BundleClientState` | `INITIAL → CONNECTING → HANDSHAKING → REGISTERING → READY → RECONNECTING → CLOSING → CLOSED` |
| `ConnectionSupervisor` | Owns Netty transport + reconnect loop. `start()` future resolves at first `READY` |
| `BundleCorrelationTracker` | `UUID → CompletableFuture<Response>`. Scheduler-driven expiry. `failAll()` on shutdown only (not on disconnect — futures survive reconnect) |
| `ProcessedRequestCache` | Inbound dedup: `resolveOrClaim` → `Claimed / InFlight / Replay`. LRU + TTL |
| `HandlerRegistry` | `payloadType → RequestHandler / NotificationHandler` (pure byte arrays) |
| `HelloFactory` | Builds `Hello` from `BundleClientConfig` |
| `InboundDispatcher` | Pattern-matches: `Response` → tracker; `Request` → dedup + handler + reply; `Notification` → handler. All handlers on virtual-thread executor |
| `EventoServerAdapter` | Implements `EventoServer` (the gateway SPI) over `BundleClient` |
| `BundleInboundDispatcher` | Bridges `HandlerRegistry.RequestHandler` ↔ v2 CBOR byte-array contract for `@CommandHandler` / `@QueryHandler` annotated classes |
| `BundleAdminRequestHandler` | Handles `evento:server-admin-request` — decodes `EventoRequest`, dispatches consumer ops, encodes `EventoResponse` |

### 4.4 Consumer Engines (`evento-bundle`, `com.evento.application.consumer.*`)

| Class | Role |
|---|---|
| `ProjectorEngine` | Composes `ConsumerProcessor` + `ConsumerStateStore` + `DeadEventQueue`. Virtual-thread run loop |
| `SagaEngine` | Adds `SagaStateStore`; per-event saga instance lookup by association |
| `ObserverEngine` | "At-least-once" delivery + `DedupeStore` |
| `EngineSupervisor` | Virtual-thread executor per engine + `shutdown(Duration deadline)` with `awaitTermination → shutdownNow` |
| `ConsumerHandle` | Admin surface (status, dead queue, retry) — implemented by all engines |
| `ConsumerEngineConfig` | Builder-side bundle of SPIs: `ConsumerStateStore`, `ConsumerLock`, `SagaStateStore`, `DeadEventQueue`, `DedupeStore` |
| `DispatchContext` | Groups `TracingAgent`, `BiFunction<String,Message<?>,GatewayTelemetryProxy>`, `MessageHandlerInterceptor` — reduces constructor arity |

### 4.5 Consumer State SPI (`evento-common`, `com.evento.common.messaging.consumer.*`)

| SPI | Responsibility |
|---|---|
| `ConsumerStateStore` | Checkpoint read/commit (optimistic versioning) + enabled flag + error history |
| `ConsumerLock` | Cross-JVM exclusive zone per `consumerId`. `LockHandle: AutoCloseable` |
| `SagaStateStore` | Saga instance lookup by association + insert/update/delete |
| `DeadEventQueue` | Per-consumer DLQ with retry flag |
| `DedupeStore` | Observer dedupe with sweep windows |

All five have in-memory impls under `.impl/` (for tests) and JDBC impls in
`evento-consumer-state-store-jdbc` (Postgres + MySQL, Flyway migrations in
`src/main/resources/db/migration/{postgres,mysql}/v2/V1__init_v2_consumer_state.sql`).

**`ConsumerProcessor`** owns the v1-shape consume loop (fetch → process → checkpoint) and is
composed by the engines. It holds no state; correctness comes from the lock + optimistic
version on the checkpoint.

### 4.6 Command Broker (`evento-server`, `com.evento.server.es.CommandBrokerHandler`)

`CommandBrokerHandler` is a Spring `@Component` that subscribes to `BusEvent.BundleDiscovered`
and registers `LocalRequestHandler` instances in `BusLifecycle` for each `AggregateCommandHandler`
and service `CommandHandler` payload type.

**Aggregate command path:**
1. Acquire optional distributed lock (`lockId ?? aggregateId`)
2. `BrokerEventStore.fetchAggregateStory(aggregateId)` → event stream + optional snapshot
3. Wrap `DomainCommandMessage` → `DecoratedDomainCommandMessage` (state + event stream attached)
4. `BusLifecycle.forward(...)` to aggregate bundle
5. `BrokerEventStore.publishEvent(DomainEventMessage)`  ± `saveSnapshot`
6. Return stored event to caller

**Service command path:** same flow, no decoration, stores `ServiceEventMessage`.

`BrokerEventStore` interface (4 methods) decouples `CommandBrokerHandler` from the concrete
Spring JPA `EventStore` — enables in-memory test implementations.

`PgDistributedLock`: when `DataSource` is null, falls back to JVM-only semaphore (safe for
single-JVM/embedded scenarios like integration tests).

---

## 5. Handshake & Registration Protocol

```
Bundle                          Server
  │── Hello(bundleId, instanceId, version, authToken) ──►│
  │                                              TokenValidator.validate()
  │◄── Welcome(serverVersion) ─────────────────────────── │  (or Reject)
  │── Notification(evento:bundle-registration, lean) ────►│  BundleRegistered event
  │── Notification(evento:enable) ─────────────────────── │  BundleEnabled event
  │── Notification(evento:bundle-discovery, rich) ────────│  BundleDiscovered event
  │                                              CommandBrokerHandler registers LocalRequestHandlers
```

**Why lean + discovery split:** A bundle with thousands of handlers would exceed the 16 MB Netty
frame limit if discovery was sent in one frame. Registration is lean (just payloadTypes); rich
metadata (handler schemas, payloadInfo) is sent post-enable in the discovery notification.

---

## 6. Key Design Decisions (load-bearing — don't undo without discussion)

1. **Server is payload-agnostic.** `Request`/`Response`/`Notification` carry `payloadType: String`
   + `payload: byte[]`. The server routes by string; bundles hold the business class loader and
   deserialize locally. No business class loading on the server side.

2. **Sealed `Message` + `MessageTypeRegistry`.** Adding a wire type requires: extend `permits` +
   register a byte tag. The compiler enforces exhaustive dispatch; no "accept all under package".

3. **Two correlation maps, two purposes.**
   - `CorrelationStore` — server-initiated requests with `CompletableFuture` to await.
   - `ForwardingTable` — bundle-A → server → bundle-B relays; no future, just remembers where
     to send the response. `drainByDestination` (not `drainInvolving`) on disconnect to preserve
     originator-side entries for QoS reconnect delivery.

4. **One event stream, sealed `BusEvent`.** Replaces v1's four parallel listener lists. Subscribers
   pattern-match with `switch` expressions.

5. **Lifecycle is explicit.** `BusLifecycle.start(port)` / `stop(Duration deadline)`. Spring
   `BusStarter` owns `@PostConstruct` / `@PreDestroy`. Constructors do no work.

6. **`ConnectionState.canSend()` is `true` for CONNECTED *and* DEGRADED.** DEGRADED is advisory
   backpressure (high-water mark); the TCP socket is alive. Responses are critical-path and must
   not be dropped due to backpressure.

7. **No `System.exit` anywhere.** `BundleClient.start()` returns a `CompletableFuture` that
   fails with the rejection reason. The caller decides what to do.

8. **Virtual-thread executor, not `StructuredTaskScope`, for long-running engines.** The stable
   JDK 25 `StructuredTaskScope` API is owner-thread-scoped (close auto-joins). Long-running
   engines that start in one method and stop in another don't fit. We use a virtual-thread
   executor + deadlined shutdown. Short-lived batch fan-out within an engine's `run()` can still
   open a `StructuredTaskScope` locally.

9. **Autoscale rip-out.** The framework emits performance metrics only (`PerformanceInvocationsMessage`,
   `PerformanceServiceTimeMessage`). The external orchestrator (k8s / Nomad) owns spawn and kill.

10. **`MessageHandlerInterceptor` default methods.** All 24 interceptor methods are `default` (ISP
    fix). Implementors override only the hooks they need. Void methods get empty bodies; return-type
    methods pass through the last parameter.

---

## 7. Security Model

- **Authentication:** `Hello.authToken: String`. `HandshakeHandler` → `TokenValidator.validate(token)`.
  Built-in impls: `acceptAll()` (default) and `sharedSecret(token)` (constant-time compare).
  Failed auth → `Reject(CODE_AUTH_FAILED)` → `BundleClient.start()` future fails.

- **TLS:** `NettyTransportConfig.sslContext` (nullable). When set, the pipeline prepends
  `SslHandler`. Same config record for client and server (`SslContextBuilder.forClient()` vs
  `forServer()`).

- **CBOR type whitelist:** `MessageTypeRegistry` maps byte tags to explicit classes under the
  sealed `Message` hierarchy. `allowIfSubType(Message.class)` + explicit registration per type.
  No "accept all under package" — protects against gadget-chain deserialization attacks.

---

## 8. Exactly-Once QoS

| Layer | Mechanism |
|---|---|
| **Broker (dedup incoming)** | `ForwardingDedupCache` — LRU, 5 min TTL, 50k entries. Duplicate `Request` (same `correlationId`) → replay cached `Response`; in-flight duplicate → silent drop |
| **Bundle handler side** | `ProcessedRequestCache` — `resolveOrClaim` returns `Claimed / InFlight / Replay`. Handler runs at most once per `correlationId` |
| **Reconnect delivery** | `reconnectBuffer` on `BusLifecycle` buffers responses for disconnected originators (2-minute TTL). `deliverPendingResponses` replays on reconnect |

A caller bundle can retry with the same `correlationId` and receive exactly one side-effect plus
the same response back.

---

## 9. Public API Surface (preserve across refactors)

These are the user-facing APIs — changing signatures requires a major version bump:

- **Annotation API**: `@Aggregate`, `@CommandHandler`, `@AggregateCommandHandler`, `@EventHandler`,
  `@EventSourcingHandler`, `@QueryHandler`, `@SagaEventHandler`, `@InvocationHandler`,
  `@Service`, `@Projector`, `@Projection`, `@Saga`, `@Observer`, `@Invoker`
- **Bundle bootstrap**: `EventoBundle.Builder` (fluent builder returned by `EventoBundle.builder()`)
- **Modeling types**: `AggregateState`, `Event`, `Command`, `Query`, `DomainCommandMessage`,
  `DomainEventMessage`, `ServiceCommandMessage`, `ServiceEventMessage`, `DecoratedDomainCommandMessage`
- **Gateway API**: `CommandGateway`, `QueryGateway` (injected into components)
- **Consumer SPIs**: `ConsumerStateStore`, `ConsumerLock`, `SagaStateStore`, `DeadEventQueue`,
  `DedupeStore`, `ConsumerEngineConfig`
- **Server Spring starter**: `evento.server.bus.*` properties + `BusFacade` autowiring

---

## 10. Build & Test

**Toolchain:** JDK 25 (`/usr/libexec/java_home -v 25` on macOS). Gradle 9.0 wrapper at `./gradlew`.

**Standard test run:**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
  :evento-transport-api:test \
  :evento-transport-netty:test \
  :evento-server:test --tests 'com.evento.server.bus.*' \
  :evento-common:test \
  :evento-bundle:test \
  :evento-consumer-state-store:evento-consumer-state-store-jdbc:test \
  :evento-lab:test \
  :evento-lab-microservices:evento-lab-ms-it:test
```

**JDBC integration tests** (requires Docker, Postgres + MySQL via Testcontainers):
```bash
EVENTO_RUN_JDBC_IT=true JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-consumer-state-store:evento-consumer-state-store-jdbc:test
```

**Coverage:** `./gradlew jacocoTestReport` — minimum 70% on new modules.

**Test counts (JDK 25, as of v2.0.0-rc1):**
- `evento-transport-api`: 41+
- `evento-transport-netty`: 8 (7 IT + 1 chunking)
- `evento-server`: 76+ (includes 80 MB large-payload round-trip IT, 10 disconnect scenarios)
- `evento-common`: 52 (consumer processor + in-memory SPI impls)
- `evento-bundle`: 7 (engine supervisor + admin handle)
- `evento-lab`: 31 (6 in-memory + 6 connectivity + 4 command RTT + 9 FailureMatrix + 2 ProjectorRetry + 2 Stress + 2 ConsumerInterceptor)
- `evento-lab-ms-it`: 22 (3 consumer lifecycle + 2 saga + 2 reconnect + 2 order lifecycle + 2 payment saga + 3 notification + 2 multi-context + 3 RTT/stress + 3 command RTT)
- JDBC ITs: 50+ (23 scenarios × Postgres + MySQL, gated on `EVENTO_RUN_JDBC_IT=true`)

---

## 11. Integration Test Infrastructure

### `evento-lab` — single-bundle IT harness

| Class | Purpose |
|---|---|
| `EmbeddedBroker` | Spins up a real `BusLifecycle` on an ephemeral port |
| `TestEventStoreBundleClient` | In-process event journal: handles `EventFetchRequest` + `EventLastSequenceNumberRequest`; `publishWithMetadata()` for metadata-flag injection |
| `CommandAwareEmbeddedBroker` | `EmbeddedBroker` + `CommandBrokerHandler` + `TestGatewayClient` factory |
| `CommandAwareTestEventStore` | Implements `BrokerEventStore`; in-memory store for command RTT tests |
| `LabStore` | Thread-safe in-memory singleton for cross-component assertions |

### `evento-lab-microservices` — multi-bundle IT harness

| Class | Purpose |
|---|---|
| `MsHarness` | Starts multiple `EventoBundle` instances against a shared `MsEmbeddedBroker` |
| `MsTestEventStore` | Handles `EventFetchRequest`; `publishWithContext()` for context-aware filtering |
| `MsCommandAwareEmbeddedBroker` | Multi-bundle equivalent of `CommandAwareEmbeddedBroker` |

Static singleton stores (`OrderViewStore`, `MsSagaStore`, `MsObservedEvents`) serve as
cross-bundle assertion surfaces in the same JVM.

---

## 12. Database Schema (Consumer State Store)

Tables created by Flyway V1 migration per dialect (`postgres` / `mysql`):

| Table | Purpose |
|---|---|
| `evento_v2_consumer_state` | Checkpoint + version + enabled flag + error history |
| `evento_v2_saga_state` | Saga state JSON(B) + flat `associations` JSON(B) column for fast lookup |
| `evento_v2_dead_event` | DLQ entries with retry flag |
| `evento_v2_dedupe` | Observer dedupe entries with sweep window |

JDBC locks:
- **Postgres:** `pg_try_advisory_lock(hashtext(consumerId))` — session-scoped, pins one `Connection` per `LockHandle`
- **MySQL:** `GET_LOCK(consumerId, 0)` — same pattern

---

## 13. Server REST API Surface

All controllers are under `com.evento.server.web.*` and depend on `BusFacade`.

| Controller | Prefix | Key endpoints |
|---|---|---|
| `DashboardController` | `/api/dashboard` | Cluster status snapshot |
| `ClusterStatusController` | `/api/cluster` | Connected nodes, availability |
| `BundleController` | `/api/bundle` | Bundle list, update, delete |
| `HandlerController` | `/api/handler` | Handler graph, invocation graph |
| `CatalogController` | `/api/catalog` | Payload catalog |
| `ConsumerController` | `/api/consumer` | Consumer status, dead queue, retry |
| `PerformanceController` | `/api/performance` | Performance model, time series |
| `ArtifactController` | `/api/artifact` | Bundle artifact upload |
| `FlowsController` | `/api/flows` | Handler flow visualization |
| `SystemStateStoreController` | `/api/system-state-store` | Aggregate snapshots |

Auth is handled by `AuthFilter` + `AuthService` + `TokenRole` (per-request JWT/token check).

---

## 14. Observability

- **Micrometer metrics** (planned/partial):
  - `evento.server.connections{state}`
  - `evento.server.heartbeat.lag{node}`
  - `evento.server.correlations.pending`
  - `evento.server.message.processing.duration{type}`
- **Performance store**: `HandlerInvocationCountPerformance` + `HandlerServiceTimePerformance`
  persisted by `PerformanceStoreService`, consumed via `PerformanceController`.
- **Structured logging:** SLF4J. Key=value on every bus lifecycle transition. `event=disconnect_superseded_skip` etc.
- **Zero-copy counters**: `BusLifecycle.forwardedRawCount` / `forwardedReencodedCount` — every
  Netty-to-Netty forward must use the raw path; tests pin this contract.

---

## 15. Commit & Code Conventions

- **Conventional Commits**: `feat(module): …`, `fix(module): …`, `chore(build): …`, `docs: …`.
  Commit bodies explain *why*, not what. `git show <hash>` should be self-contained.
- **No `System.exit` anywhere** in framework code. Failures surface through futures or callbacks.
- **Constructors do no work** — no thread starts, no I/O. Everything explicit via `start()`.
- **Tests at the boundary** — integration tests use real TCP (`NettyServerTransport` + `BundleClient`),
  not mocks. Mocks only for the in-memory transport test double.
- **OCP via dispatcher maps** — `MessageRouter` and `InboundDispatcher` use `Map<Class, Handler>`.
  Adding a handler type never touches existing dispatch code.
- **Records for all value types** — `Message` subtypes, `BundleRegistrationInfo`, `ConsumerCheckpoint`
  subtypes, `ConsumerErrorState`, `VersionedCheckpoint`, etc.
- **Virtual threads** for business executor (`Executors.newVirtualThreadPerTaskExecutor()`). Never
  block the Netty EventLoop (CBOR decode, handler dispatch all go to the business executor).

---

## 16. Current Status (as of merge to `main`)

Version: `2.0.0-rc1`. Tag: `v2.0.0-rc1`.

**All three rewrite phases (PR1/PR2/PR3) are shipped and merged to `main`.**  
**Remaining before `v2.0.0` GA:**
- Deploy to staging with real traffic generator
- Soak 1–2 weeks with early adopters
- Merge any staging findings, bump version → `2.0.0`, tag, publish to Maven Central

**Known post-RC1 fixes already committed:**
- Fix A: `onTransportDisconnected` superseded-session race (routing loss on reconnect)
- Fix B: FK violation in `BundleService` orphan-payload cleanup
- Fix C: `DEGRADED` channel no longer drops in-flight responses
- Fix D: In-flight responses buffered + replayed on originator reconnect
