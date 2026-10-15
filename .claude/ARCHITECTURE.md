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

Since 2.5 several servers can share one PostgreSQL event store and catalog (cluster mode, §4.7–4.8).
Each server routes only to the bundles connected to it, with no server-to-server forwarding
(G1, a design choice): the intended topology is a *cell*, a server with a complete set of
handlers next to it. Bundles fail over along an ordered address list and fail back to the first
address once it is healthy again.

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
├── evento-gui/                   Web GUI (Angular 22 + Ionic 8), built into the server's static resources
├── evento-lab/                   In-process integration tests (single-bundle RTT, failure matrix, etc.)
├── evento-lab-microservices/     Multi-bundle integration tests (RECQ microservices scenario)
│   ├── evento-lab-ms-api/
│   ├── evento-lab-ms-command/
│   ├── evento-lab-ms-query/
│   ├── evento-lab-ms-saga/
│   ├── evento-lab-ms-observer/
│   └── evento-lab-ms-it/
└── evento-lab-cluster/           Multi-server, multi-region lab (Docker compose, toxiproxy WAN, chaos S00–S18)
    ├── lab-api/  lab-support/    payloads; env-driven BundleDeployment/BundleRunner shared by every image
    ├── order-command/ order-query/ order-saga/ order-observer/ order-gateway/   one image each
    ├── evento-lab-cluster-chaos/  scenario driver (gated on EVENTO_LAB_CLUSTER)
    └── docs/                      cluster-mode guide L0–L5, config reference, chaos results (Italian)
```

**Gradle**: version `2.5.2` (wrapper 9.7.1), Java toolchain `25` (run on JDK 25.0.3+), Spring Boot
`4.1.1` (server and lab services), Jackson `2.22.2`, Netty `4.2.17.Final`, Flyway `13.4.0`. Root
`build.gradle` centralises versions in `allprojects { ext { … } }`; the release script bumps
`version` / `eventoVersion` there and `evento-server/version.json`. The published libraries also
run on Netty 4.1 (what Spring Boot 3.x BOMs resolve): `:evento-transport-netty:netty41Test`
re-runs the transport tests against `4.1.124.Final`.

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
on message size. Since 2.6 nothing on this path copies a message more than once: the reassembler
holds retained slices in a `CompositeByteBuf`, `ChunkingEncoder` composes headers with slices,
`CborMessageEncoder` wraps the codec's array. A large answer should still be **streamed**
(§4.9), not chunked: a chunked message is reassembled whole on every hop.

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
  ├── Notification (payloadType: String, payload: byte[])
  ├── StreamData     (correlationId, seq, payload, last, failure)   ← 2.6, "SDA"
  ├── StreamCredit   (correlationId, credits)                       ← 2.6, "SCR"
  ├── StreamCancel   (correlationId, reason)                        ← 2.6, "SCN"
  └── StreamRedirect (correlationId, host, port, ticket, expiresAtMs) ← 2.6, "SRD"
```

`Request` gained three additive fields (2.6), all off the wire when unset and all carried end to
end by the broker's raw forward: `acceptEncodings` (reply compression the caller decodes),
`streamWindow` (> 0 = streamed reply, the initial credit), `streamDirect` (the caller can take
the stream on a direct connection). The four stream messages are sent **only** to a peer that
negotiated `HandshakeProtocol.CAPABILITY_STREAM` (`stream:1`): an older peer cannot decode
them and would close the channel. Every 2.6 bundle offers it; `BusLifecycle` always accepts it.

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
| `Frame` | api | Parsed `Message` + retained raw wire bytes (zero-copy) + `receivedAtMs` (stamped at decode on the I/O thread, before any dispatch queue; the broker measures request deadlines from it) |
| `ConnectionStateMachine` | api | `AtomicReference`-backed; legal transitions only |
| `ConnectionState` | api | `DISCONNECTED → CONNECTING → CONNECTED → DEGRADED → CLOSING → CLOSED`. `canSend()` is `true` for `CONNECTED` **and** `DEGRADED` (DEGRADED is advisory backpressure — socket is alive) |
| `ExponentialBackoffWithJitter` | api | `min(maxBackoff, base × 2^attempt) × (1 ± jitter)`; default base=500 ms, max=30 s, jitter=0.2 |
| `MessageTypeRegistry` | api | `byte tag ↔ Class<? extends Message>` whitelist |
| `JacksonCborCodec` | api | Default `Codec` impl (Jackson CBOR) |
| `InMemoryTransport` | api | Test double; `simulateDisconnect()` → DISCONNECTED (not DEGRADED) |
| `NettyClientTransport` | netty | Client-side Netty channel + reconnect |
| `NettyServerTransport` | netty | Server-side accept loop; each accepted channel → `ServerChildTransport` |
| `MessageInboundHandler` | netty | Terminal pipeline handler: hands every inbound `Frame` to the business executor (virtual threads), never the event loop; `StreamData` frames go through the channel's `SerialLane` instead, so a stream's frames keep their order |
| `SerialLane` | netty | Runs tasks one at a time in submission order on the shared executor, holding no thread between bursts (2.6) |
| `InboundDeliveries` | netty | Per-channel count of `Response` frames read but not yet delivered; the `DISCONNECTED` transition (channel inactive, server-child and client close listeners) waits for them, bounded by 2 s (`event=disconnect_after_delivery_grace`). See §6 decision 14 |
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
| `ClusterRegistry` | `payloadType → Set<NodeAddress>` + `instanceId → zone`. `RANDOM` (default) / `FIRST` / `LOCALITY_PREFERRED` pick strategy, chosen by `evento.server.bus.pick-strategy`; `pickFor(type, originator)` picks on behalf of the requesting bundle |
| `CorrelationStore` | `ConcurrentHashMap<UUID, PendingCorrelation>`. Bounded shutdown; scheduler-driven expiry |
| `ForwardingTable` | `correlationId → (originatorAddress, destinationAddress)`. `drainByDestination(addr)` removes only destination-side entries (leaves originator-side alive for QoS reconnect delivery). A streamed request keeps its entry for the whole stream (`touch` per frame, pruned by inactivity, `drainStreamsByOriginator` on caller loss) |
| `ForwardingDedupCache` | LRU, 5 min TTL, 50k entries, 128 MiB of retained replies, 1 MiB per reply (a larger one is remembered as answered: `DuplicateRequestException` to a duplicate). Deduplicates retried `Request`s with side effects; straight payload types never enter it |
| `HandshakeHandler` | Validates `Hello` → calls `TokenValidator` → calls `BusLifecycle.onHello` |
| `TokenValidator` | SPI: `acceptAll()` (default) or `sharedSecret(token)` (constant-time compare) |
| `MessageRouter` | OCP dispatcher — `Map<Class<? extends Message>, Handler>` registered in `start()` |
| `BundleSession` | Per-connection mutable state: `NodeAddress`, registered handlers, enable/disable flag |
| `BusEvent` | Sealed hierarchy: `BundleRegistered`, `BundleDiscovered`, `BundleLeft`, `AdminNotification`, …  |
| `BusEventBus` | `subscribe(Consumer<BusEvent>)` replaces v1's four parallel listener lists |

**Reconnect QoS (Fix D):** When a handler replies while the originator is disconnected,
`BusLifecycle.onResponse` buffers the `Response` in `reconnectBuffer` (keyed by `instanceId`,
2-minute TTL, bounded by `setReconnectBufferMaxBytes`, 256 MiB: a reply over it is not buffered,
`event=reconnect_buffer_full`). `onHello` calls `deliverPendingResponses` on re-handshake.

**Straight payload types (2.6).** Reads are routed without any of that: no dedup slot, no
cached reply, no reconnect buffering (`event=straight_response_dropped_originator_away`).
Bundles declare their `@QueryHandler` types in `BundleRegistrationInfo.queryPayloadTypes`;
server-local reads (`EventFetchRequest`, `EventLastSequenceNumberRequest`) are registered by
`EventStoreRequestHandlerBridge` (`registerStraightPayloadType`). A caller asks a lost read again
(`BundleCorrelationTracker.resendSentBefore`, §4.3). The broker's own replies are DEFLATE-compressed
for callers that accept it (`setResponseCompressionThreshold`, 32 KiB).

**Stream relay (2.6, `BusLifecycle.onStream`).** `StreamData`/`StreamRedirect` from the
destination go to the originator, `StreamCredit`/`StreamCancel` from the originator to the
destination, each as the raw frame (zero copy), only between sessions that negotiated
`stream:1` (`BundleSession.supportsStreams`); a frame from any other session is dropped
(`event=stream_frame_foreign`). A frame for an unknown stream, or whose caller is gone, is
answered with `StreamCancel` so the handler stops. The last data frame, a cancel or a redirect
ends the entry; the caller's disconnect cancels its streams at their handlers. Counters:
`streamsOpened`, `activeStreams`, `streamFramesRelayed`, `streamBytesRelayed`.

**Supersede race (Fix A):** `onTransportDisconnected` returns early when `ConnectionRegistry.unregister`
returns empty (token mismatch = superseded session). This prevents wiping the handlers of a
reconnected bundle.

### 4.3 Bundle Client (`evento-bundle`, `com.evento.application.client.*`)

| Class | Role |
|---|---|
| `BundleClient` | Public façade: Builder, `start/stop`, `request(payloadType, byte[], Duration)`, `notify(...)`, `enable/disable`, `registerRequestHandler/NotificationHandler` |
| `BundleClientConfig` | Record + Builder: host/port list, identity, authToken, capabilities, timeouts, transport config, autoEnable |
| `BundleClientState` | `INITIAL → CONNECTING → HANDSHAKING → REGISTERING → READY → RECONNECTING → CLOSING → CLOSED` |
| `ConnectionSupervisor` | Owns Netty transport + reconnect loop over the ordered endpoint list (next endpoint after a session that fails before READY, endpoint 0 after a READY session drops). `start()` future resolves at first `READY`. Runs the fail-back thread (below) |
| `BundleCorrelationTracker` | `UUID → CompletableFuture<Response>`. Scheduler-driven expiry. `failAll()` on shutdown only (not on disconnect — futures survive reconnect). Idempotent reads (`request(..., idempotent = true)`: queries, event fetches) remember their `Request` and session epoch: `resendSentBefore` sends them again on the next READY session (same id, remaining budget), and `failSentOnOtherBroker` leaves them alone |
| `ProcessedRequestCache` | Inbound dedup: `resolveOrClaim` → `Claimed / InFlight / Replay`. LRU + TTL |
| `HandlerRegistry` | `payloadType → RequestHandler / NotificationHandler` (pure byte arrays) |
| `HelloFactory` | Builds `Hello` from `BundleClientConfig` |
| `InboundDispatcher` | Pattern-matches: `Response` → tracker; `Request` → dedup + handler + reply; `Notification` → handler. All handlers on virtual-thread executor |
| `EventoServerAdapter` | Implements `EventoServer` (the gateway SPI) over `BundleClient` |
| `BundleInboundDispatcher` | Bridges `HandlerRegistry.RequestHandler` ↔ v2 CBOR byte-array contract for `@CommandHandler` / `@QueryHandler` annotated classes |
| `BundleAdminRequestHandler` | Handles `evento:server-admin-request` — decodes `EventoRequest`, dispatches consumer ops, encodes `EventoResponse` |
| `stream.InboundStream` / `stream.OutboundStream` | The caller's and the handler's end of a streamed reply: ordered frames and credit returned by half-windows / frames sent only against credit, cancel on idle timeout (2.6, §4.9) |
| `stream.FrameQueryStream` | `QueryStream<V>` over an `InboundStream`: decodes batches, whole answers and `ExceptionWrapper` frames |
| `stream.DirectStreamServer` / `DirectStreamClient` / `DirectStreamConfig` | Bundle-to-bundle streams: ticketed endpoint, redirect follower with broker fallback (2.6, §4.9) |

**Fail-back to the preferred endpoint (N16).** While a session is READY on any endpoint but 0
and `BundleClientConfig.failbackActive()` (enabled, default true, and 2+ endpoints), a virtual
thread probes endpoint 0 every `failbackProbeInterval` (5 s): TCP connect + transport `Ping`,
answered by the broker's `HeartbeatHandler` below the `MessageRouter`, on a separate connection
that never sends `Hello`. That is the safety argument: `BusLifecycle.onHello` is the only path
that touches shared state (`ConnectionRegistry.register` → `NodeJoined`, zone, reconnect-buffer
replay, supersede), so a probe shows up on the broker only as `session_accepted` /
`preregistered_disconnect`, both at DEBUG for a connection that sent no frame. The probe checks
readiness, not just liveness: `HeartbeatHandler` answers a Ping that precedes any other frame only
while `TransportServer.setPreSessionReadiness` says so — `BusLifecycle.setProbeReadiness`, which the
Spring server wires to Boot's `ReadinessState.ACCEPTING_TRAFFIC` (the bus binds in a
`@PostConstruct`, long before JPA / event store are up). Sessions past their Hello are never gated;
an old server answers every Ping (no wire change). A plain socket pre-check runs first while
endpoint 0 is down, so a dead broker produces no transport WARN per probe; the probe's own
connections log at DEBUG, wait for the transport's `connectTimeout` before giving up on a connect,
and `NettyClientTransport.close()` aborts a connect still in flight (it used to leak the channel).
After every probe has answered for `failbackStabilityWindow` (30 s) + random `failbackJitter`
(≤ 10 s) + the whole jitter range again for bundles with no `handlerPayloadTypes` (callers after
handlers that started their window together, G1), `performFailback`: `draining` gate (`send`
refuses new `Request`s with `SendFailedException`, i.e. definitely not sent; replies and
notifications pass; `evento:enable` is deferred to the next session, ordered against the drain by
a lock so it cannot re-enable the broker being left) → `evento:disable` on the current broker →
wait (≥ 250 ms grace, ≤ `failbackDrainTimeout`, 10 s) for `InboundDispatcher.inFlightRequests()`
(decremented when the reply's write completes, so a chunked reply is not cut off; a written
reply can still race the close on the broker's side, which since 2.5.2 delivers responses it has
read before announcing the disconnect, §6 decision 14) and
`BundleCorrelationTracker.pendingCount()` to reach 0 → set `failbackRequested` (cleared again if
the session ended meanwhile), close. The run loop dials endpoint 0 without backoff; identity
checks run on that handshake as usual. Leftover outbound requests get `BrokerChangedException`
from the new `Welcome` (G4 semantics); a leftover inbound reply is lost (the old broker already
failed that call with "peer disconnected"). **Reactive switch:** a request answered with "no
handler for" (or "no available handler for") while away from endpoint 0 calls
`ConnectionSupervisor.noHandlerOnCurrentBroker`; if the latest probe of endpoint 0 was answered
(or the next one, within 2 probe intervals, is) the switch starts at once instead of after the
window — in an HA pair the fallback has no other handler once the cell's handlers are home. It
never probes on its own and is off after a failed switch. A switch that does not end READY on
endpoint 0 counts as `failbacksFailed` and doubles the next window (≤ 16×); a failed probe
restarts the window (no ping-pong with a flapping broker). A switch refused by the broker
(`BrokerRefusedException`: identity mismatch, protocol, `Reject`) `MAX_FAILBACK_REFUSALS` (3) times
in a row suspends fail-back (ERROR `failback_suspended`, no more probing) until a session becomes
READY on endpoint 0 by other means or the process restarts; any READY on endpoint 0 resets the
failure count. `CLOSING`/`CLOSED` are final in `transitionToForce`, so `stop()` racing the
immediate redial cannot leave a supervisor running; the probe event loop (built through
`NettyEventLoops`) is released on stop and on give-up. Logs: `event=failback_probe_healthy` /
`failback_reactive_armed` / `failback_reactive` / `failback_started` / `failback_drained` /
`failback_drain_timeout` / `failback_completed` / `failback_failed` / `failback_suspended` /
`failback_resumed` / `enable_deferred`. Counters: `BundleClient.failbacksCompleted()`,
`failbacksFailed()`, `reactiveFailbacks()`, `failbackProbesAnswered()`, `failbackSuspended()`,
`failbackDraining()`. A request refused while not sendable reaches the v1 gateways as
`RequestNotSentException` (an `IllegalStateException`): definitely not executed, safe to retry.
`BundleClient.enable()` never fails for want of a connection (latched, sent by the next session)
and `EventoBundle` registers its consumer re-registration listener before the first send, so a
startup gate that opens during a drain or a reconnect still starts sagas, observers, the stats
publisher and the started hook. Residual: fail-back is per bundle, not per cell. Handlers move
first; a caller still on the fallback then gets one "no handler" per request until its reactive
switch completes (one probe interval at most, plus the drain) — not a whole window. A
single-address VIP has no endpoint 0 to probe. Tests: `BundleClientFailbackIT`,
`ProbeConnectionIT`, `StartupDuringFailbackIT` (evento-lab).

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

**Startup gate (bundle-enable timing).** By default the bundle enables on the cluster
immediately after registration; projectors align to the event-stream head in the
background (each logs `Projector head reached: …` when it catches up), and saga/observer
engines start right away. A projector annotated `@Projector(waitForHeadReached = true)`
opts into gating: the bundle stays disabled — and sagas/observers deferred — until every
gating projector (one gate per context) has reached head (v1's unconditional behaviour).
Only gating engines participate in the alignment counter that releases the enable gate.

### 4.5 Consumer State SPI (`evento-common`, `com.evento.common.messaging.consumer.*`)

| SPI | Responsibility |
|---|---|
| `ConsumerStateStore` | Checkpoint read/commit (optimistic versioning) + enabled flag + error history |
| `ConsumerLock` | Cross-JVM exclusive zone per `consumerId`. `LockHandle: AutoCloseable` |
| `SagaStateStore` | Saga instance lookup by association + insert/update/delete |
| `DeadEventQueue` | Per-consumer DLQ with retry flag |
| `DedupeStore` | Observer dedupe with sweep windows |
| `ConsumerExecutor` | Named, bounded execution resource for parallel consumers (`ConsumerExecutors.virtual/pooled/unbounded`) |

All five have in-memory impls under `.impl/` (for tests) and JDBC impls in
`evento-consumer-state-store-jdbc` (Postgres + MySQL, Flyway migrations in
`src/main/resources/db/migration/{postgres,mysql}/v2/V1__init_v2_consumer_state.sql`).

**`ConsumerProcessor`** owns the v1-shape consume loop (fetch → process → checkpoint) and is
composed by the engines. It holds no state beyond per-consumer in-flight counters;
correctness comes from the lock + optimistic version on the checkpoint.

**Async consumers.** A handler annotated `@EventHandler(executor = "name")` is dispatched to
the named `ConsumerExecutor` instead of running inline, giving parallel consumption for
idempotent / overwrite handlers. Executors are registered on the bundle builder
(`.addConsumerExecutor(...)`), shared bundle-wide by name, and resolved per event by
`ConsumerExecutorResolver`; unknown names fail start-up (`ConsumerExecutorValidator`).

The defining rule: **the checkpoint advances when a task starts, not when it completes.**
That bounds how far the loop may run ahead (never more than the executor's capacity) and
makes the crash-loss window the set of running tasks rather than a queue depth. Submit is
time-boxed, so a saturated executor ends the cycle and releases the consumer lock instead of
parking on it with a pooled JDBC connection pinned. Consequences — at-most-once for
in-flight events on a hard kill, `retry = -1` coerced to `0`, no ordering guarantee, sagas
excluded, and the pool-sizing arithmetic in §12 — are detailed in
[`ASYNC-CONSUMERS-PLAN.md`](ASYNC-CONSUMERS-PLAN.md).

Both traded-away guarantees can be bought back independently:

| Want | Use | Cost |
|---|---|---|
| At-least-once delivery | `CheckpointMode.WATERMARK` (`EventoBundle.Builder.setCheckpointMode`) — persists the highest *contiguous completed* sequence; the fetch cursor stays on the in-memory dispatch frontier so nothing is reprocessed within a run. Sequence numbers the store never returns (burned by rolled-back inserts) are closed from the batch that skips them, or the first one would pin the watermark for ever (2.4.6) | A crash replays the in-flight window; the dashboard's "last event" trails by it |
| Per-aggregate ordering | `ConsumerExecutors.partitioned(name, lanes)` — equal aggregate ids pin to one lane, one task per lane | A hot aggregate serialises; concurrency bounded by lanes and reduced by key skew |

`MessageHandlerInterceptor` keeps working for transaction management because
`before → handler → after/onException` always run on **one thread** — the executor's task
thread under async dispatch. Implementations must be thread-safe and keep per-invocation
state in `ThreadLocal`s.

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

**Caller deadline (N3).** `CommandBrokerHandler` registers as a
`BusLifecycle.DeadlineAwareRequestHandler`. The deadline is `Frame.receivedAtMonotonicMs() +
Request.timeoutMillis` (`Request.deadlineFrom`): the sender's remaining budget, which every
sender has always filled from its request timeout, added to the local arrival time, so no
clocks of different machines are compared (the deadline errs late by the one-way delay, never
early). Arrival and every later check use the monotonic clock (`System.nanoTime`,
`BusLifecycle.monotonicMillis()`), so an NTP step neither expires every queued command at once
nor stretches deadlines. `timeoutMillis = 0` means no deadline (old behaviour). A request nobody
waits for any more is dropped unexecuted at four points, each logging `event=request_expired_dropped
stage=...`, counting in `evento.server.requests.expired{stage}` and answering
`RequestExpiredException`. The caller normally never sees that answer: the broker's deadline is
never earlier than the caller's own timer, so the caller has already failed the request with the
indeterminate `RequestTimeoutException`. The gain is non-execution, visible in the metric; callers
must not branch on `RequestExpiredException`.

| Stage | Where | What is skipped |
|---|---|---|
| `queued` | `BusLifecycle.onRequest`, before routing (any request, local or forwarded) | everything; covers time queued for a dispatch thread |
| `lock_wait` | `acquire(key, remaining)`; the lock wait is bounded by the deadline | lock, fetch, forward, append |
| `before_forward` | after lock + story fetch | forward, append |
| `before_append` | aggregate commands only: the handler bundle did not answer in time (the forward budget is what is left of the deadline, not a fresh timeout), or answered after it | append, snapshot |

A **service** command is dropped only before its lock and before its forward. Once forwarded it
gets the command's own timeout, and its event is stored whenever the handler answers, even after
the deadline (`event=service_event_stored_after_deadline`): the handler has side effects (a
payment, a mail), and dropping the event would hide an effect that happened from every consumer
and tell a retrying caller "not executed". A service forward that fails after the deadline stays
an indeterminate error.

What stays open: an append that starts before the deadline and ends after it is indeterminate
for the caller; a retry with an idempotency key (G5, §8) resolves it: the retry is answered with
the stored event.

`PgDistributedLock`: when `DataSource` is null, falls back to JVM-only semaphore (safe for
single-JVM/embedded scenarios like integration tests). With a `DataSource` (production) it is
one shared Spring bean (`DistributedLockConfiguration`) over a **dedicated Hikari pool**
(`evento.lock.pool.maximum-size`, default 10; `evento.lock.pool.connection-timeout-ms`, default
1000): each *held* lock owns one pooled connection from acquire to release, so holders never pin
the connections their own work needs, and a waiter polls `pg_try_advisory_lock` with jittered
backoff holding no connection between attempts. The lock keeps one permit per pool connection
and takes it before borrowing, so an exhausted pool never costs the pool's connection timeout:
`tryAcquire` (telemetry) fails at once, `acquire(key, maxWait)` waits at most `maxWait`. Waits
are bounded (`evento.lock.acquire.timeout.ms`, a Spring property, 60 s, or `acquire(key, maxWait)`)
→ `LockAcquisitionTimeoutException`, logged at WARN only when the lock's own bound ran out (a wait
cut by the caller's deadline is the broker's `lock_wait` drop, DEBUG here); `verifyHeld` / `release`
detect a lock whose session died → `LockLostException` (`event=lock_lost`). A connection whose
session may still hold a lock is aborted *and* closed, so Hikari evicts it and the slot comes back.
The key `es-lock` is the event store's append lock; any other key whose `hashtext` collides with it
is locked as `hashtext(key || '#evento-lock')` (`PgDistributedLock.KEY_EXPR`), so a caller-chosen
`lockId` can never make its holder wait on its own append. Budget
`servers × (spring.datasource.hikari.maximum-pool-size 20 + lock pool 10)` under `max_connections`:
90 for three servers, inside PostgreSQL's default 100 (idle: `minimum-idle` 5 + 2 per server).

### 4.7 Shared Catalog Across Servers (`com.evento.server.service.discovery.*`)

Several servers share one `core__*` catalog, but each server's connection view is in-JVM only.

| Piece | Role |
|---|---|
| `core__bundle_instance` + `BundlePresence` | **Presence table**: `instance_id → (bundle_id, server_instance_id, updated_at)`. A server claims the row on `NodeJoined`, refreshes its rows every `evento.server.presence.heartbeat-interval-ms` (30 s) and reaps its own unrefreshed ones; rows older than `evento.server.presence.stale-after-ms` (180 s, database clock) no longer count as live. On `NodeLeft` a server cleans up only if it still owns the row (`Release.OWNED`/`UNTRACKED`); `OWNED_ELSEWHERE` means the instance failed over and its fresh registration on another server is left alone (G7). Requires a unique server instance id per server |
| `ConsumerService.catalogLockKey` | One advisory-lock key per bundle, `DISCOVERY:<bundleId>`, for discovery, consumer registration and leave cleanup (they used to race under different keys, G8) |
| `CatalogWriter` | Race-free inserts (`INSERT ... ON CONFLICT DO NOTHING`) for bundle, component, payload and consumer rows; shared payload rows are created up front in name order and corrected after the discovery transaction commits, so two discoveries cannot deadlock |
| `AutoDiscoveryService` / `ConsumerService` | One transaction per discovery / registration pass; a pass that loses a race (40P01, 40001, FK 23503, vanished row) is retried whole |

**Component identity is per bundle (N1).** `core__component.id = bundle_id || ':' ||
component_name` (`Component.idOf`), with a unique `(bundle_id, component_name)` index;
`core__handler.component_id` and `core__consumer.component_id` reference it. The same class
deployed by several bundles (one generic `OrderProjector` in a bundle per region) therefore has
one row per bundle, and `BundleService.removeBundle` deletes only that bundle's handlers,
consumers and components. Consumer rows are keyed by `instanceId_consumerId`, and the consumer
id is `bundleId_Component_version_context`, so one bundle id run by several instances with
different contexts lists every lane and a leaving instance removes only its own
(`clearInstance`). Catalogs created before this are re-keyed in place, once, by
`AdvisoryLockedSchemaInitializer` (one transaction under the schema lock,
`event=catalog_component_rekeyed`) when Boot runs `schema.sql` (`spring.sql.init.mode=always`).
With the schema managed outside the server the boot fails on a pre-N1 catalog, naming the shipped
script `classpath:db/upgrade/n1-component-id.sql`; a missing `es__events` primary key is only
warned about (`db/upgrade/es-events-primary-key.sql`). The server refuses PostgreSQL older than 12. REST/GUI identify a component by `componentId`;
`GET /api/catalog/component/{identifier}` also takes a bare name when it is unambiguous
(`409` with the candidate ids otherwise).

### 4.8 Cluster Configuration (server properties, bundle builder options)

| Setting | Default | Meaning |
|---|---|---|
| `evento.server.instance.id` / `evento.cluster.node.server.id` (`EVENTO_SERVER_INSTANCE_ID`, `EVENTO_CLUSTER_NODE_SERVER_ID`) | `server-0` / `iris-evento-server` | Must be unique per server: presence ownership and the `Welcome.serverInstanceId` depend on it |
| `evento.cluster.name` (`EVENTO_CLUSTER_NAME`) | `evento-server` (shipped properties; empty = not announced) | Announced in `Welcome.clusterName`; bundles may require it (`withExpectedClusterName`) instead of one server id (G13). Set it per deployment: every stock server announces `evento-server`, so requiring that value protects nothing (`event=cluster_name_shipped_default` WARN at boot) |
| `evento.server.bus.pick-strategy` | `random` | `random`, `first`, `locality-preferred` (§6 decision 11) |
| `spring.datasource.hikari.maximum-pool-size` / `minimum-idle` | 20 / 5 | Application pool |
| `evento.lock.pool.maximum-size` / `evento.lock.pool.connection-timeout-ms` | 10 / 1000 | Dedicated lock pool (§4.6) |
| `evento.lock.acquire.timeout.ms` (`EVENTO_LOCK_ACQUIRE_TIMEOUT_MS`; `-D` system property as fallback) | 60000 | Bound on any lock wait |
| `evento.server.presence.heartbeat-interval-ms` / `evento.server.presence.stale-after-ms` | 30000 / 180000 | Presence table (§4.7). Not independent: boot fails when stale-after <= heartbeat and warns below 3 x heartbeat; the heartbeat has its own scheduler thread (`spring.task.scheduling.pool.size=2`) so the hourly telemetry sweep cannot delay it |
| `evento.es.schema.enforce-primary-key` | true | `es__events` PK added on boot; `false` only to boot over existing duplicates |
| `evento.es.mode` | `APES` in code, `CPES` shipped | Use CPES with several servers |

Bundle side (`EventoBundle.Builder`): `EventoServerMessageBusConfiguration` with several
`ClusterNodeAddress`es (ordered failover, G2; fail-back to the first once it has been healthy for
the stability window, N16: `setFailbackEnabled` / `setFailbackProbeInterval` /
`setFailbackStabilityWindow` / `setFailbackJitter` / `setFailbackDrainTimeout`, defaults on / 5 s /
30 s / 10 s / 10 s; a bundle without request handlers waits the jitter range once more, and any
bundle switches at once on a "no handler" reply while the first address answers probes),
`withExpectedServerInstanceId` / `withExpectedClusterName`; `setZone` (the `zone:<Z>` Hello
capability); `setTransportConfig`, `setDefaultRequestTimeout` (also the caller deadline the broker
enforces), `setMaxInFlightRequests`; `setComponentContexts(Component.class, ctx...)` starts **one
consumer engine per context** (own consumer id, checkpoint, lock); no contexts = the wildcard
`Context.WILDCARD` (`"*"`). A context literally named `"ALL"` is an ordinary context; the
deprecated `Context.ALL` constant is the wildcard, not `"ALL"`.

### 4.9 Query Path (2.6): straight reads, streams, local answers, read-your-writes

| Concern | Where | How |
|---|---|---|
| Straight reads | `BusLifecycle.isStraight`, `BundleCorrelationTracker.resendSentBefore` | §4.2: queries never cached nor buffered by the broker; the caller asks a lost one again |
| Streamed answers | `QueryGateway.stream` → `EventoServerAdapter.streamQuery` → `BundleClient.openStream` → broker → `InboundDispatcher.runStream` → `BundleInboundDispatcher.handleStream` | Views in `QueryStreamBatch` frames (~`setStreamFrameBytes`, 256 KiB, batch size adapts), sent against credit (`QueryOptions.window`, 16); `ExceptionWrapper` frame for a query failure; plain `Response` when a side cannot stream |
| Lazy answers | `Streamed<T>` | Source opened after the handler returns (outside interceptors), read as frames go, closed at end/cancel/failure; `materialize()` for a plain reply |
| Pages | `Page<T>`, `PageRequest` | Offset (optional total) or cursor; `next()` gives the following request |
| Direct streams | `DirectStreamServer` (handler) / `DirectStreamClient` (caller) | `streamDirect` request → `StreamRedirect` (random single-use ticket, bound to the correlation id, TTL 10 s) via the broker → caller connects, `Hello.authToken` = ticket → frames on that connection; any failure to establish → `BundleClient.reaskViaBroker` (new correlation id, plain broker stream) |
| Local answers | `BundleClient.answersLocally` / `requestLocally` / `openStream`, `InboundDispatcher.answerLocally` | Own query + short-circuit on + bundle enabled → the request bytes run through the handler path in process (same codec, gate, interceptors, tracing, metrics); streams through an in-process pipe with the same credit |
| Read-your-writes | `EventoResponse.eventSequenceNumber/eventContext` (broker) → `EventoServerAdapter.recordWrite` (`ownWrites`, `EventMessage.storedAt`) → `QueryMessage.consistency` → `ProjectorConsistencyGate` → `ProjectorEngine.awaitPosition` | Per-context positions; the gate waits for the projectors of those contexts (wildcard: the highest), by `ConsumerProcessor.knownCheckpoint` or the store's checkpoint (another instance's lane, read at most every 50 ms), wakes the consume loop, and throws `ConsistencyTimeoutException` at the caller's deadline less a margin (a tenth, ≤ 250 ms) |
| Compression | `PayloadEncoding` | Per request (`acceptEncodings`), end to end, marker `0xFF 'E' 'Z' 0x01` + zlib; ≥ 32 KiB and ≥ 1/8 saved |

---

## 5. Handshake & Registration Protocol

```
Bundle                          Server
  │── Hello(bundleId, instanceId, version, authToken) ──►│
  │                                              TokenValidator.validate()
  │◄── Welcome(serverVersion, serverInstanceId) ───────── │  (or Reject)
  │   bundle checks serverInstanceId against its expected id, if set (2.4.6)
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

9. **Framework does not manage instance lifecycle.** The framework emits performance metrics only
   (`PerformanceInvocationsMessage`, `PerformanceServiceTimeMessage`); instance lifecycle and scaling
   are owned entirely by the external orchestrator (k8s / Nomad).

10. **`MessageHandlerInterceptor` default methods.** All 24 interceptor methods are `default` (ISP
    fix). Implementors override only the hooks they need. Void methods get empty bodies; return-type
    methods pass through the last parameter.

11. **Locality is a capability, not a wire field (G12).** A bundle declares its zone with
    `EventoBundle.Builder.setZone("EU")` / `BundleClient.Builder.zone(...)`; it travels as the
    `zone:<ZONE>` string in `Hello.capabilities`, so no `Message` type changed and an old broker
    simply ignores it. With `evento.server.bus.pick-strategy=locality-preferred` the broker picks
    among available handlers in the *originator's* zone and falls back to all handlers when that
    zone has none: it never refuses a request `RANDOM` would have routed. The originator is the
    session that sent the `Request`; server-local handlers that forward on their own
    (`CommandBrokerHandler`) receive it through `OriginatorAwareRequestHandler`, so brokered
    commands keep the caller's zone too. Server-initiated forwards with no originator (admin RPCs)
    and zone-less callers get `RANDOM`. Zones are per-server state like the views
    (`GET /api/cluster-status/zones`; env `EVENTO_SERVER_BUS_PICK_STRATEGY` in the stock `application.properties`). Routing by event *context* (the region a command belongs to)
    is a different, larger change: commands carry no framework-level context (G23).

12. **A request's deadline is its arrival plus the sender's budget, never the sender's clock
    (N3).** `Request.timeoutMillis` (on the wire since 2.0) is read as the remaining budget and
    added to `Frame.receivedAtMs`, stamped on the I/O thread before the dispatch queue. Comparing
    the sender's `timestampMs` with the server clock would drop or keep requests according to
    clock skew between regions; the chosen form can only err late, by the one-way delay. No
    field was added, so old bundles are protected too; `0` keeps the old no-deadline path.

13. **Catalog rows are identified per bundle (N1).** A component is `(bundle, name)`, stored as
    the deterministic string `bundle:name` rather than a composite JPA key, so `CatalogWriter`
    keeps single-statement `ON CONFLICT` inserts and lookups are computed, not queried. Never key
    a catalog row by a class name alone: the same class is routinely deployed in several bundles.

14. **A disconnect is announced after the responses already received (2.5.2).** Inbound frames
    are dispatched to the business executor, but a channel's close is seen on the event loop. A
    peer that writes a reply and closes at once (the end of a fail-back drain) used to lose that
    reply: the broker's disconnect cleanup drained the forwarding entry and failed the originator
    with "peer disconnected" before the reply was routed. `InboundDeliveries` orders the
    transition after pending `Response` deliveries, with a 2 s bound so a stuck listener cannot
    keep a dead session alive. Only responses are tracked: a request still being served must
    not delay cleanup. Do not move the state transition back onto the close listener directly;
    `NettyTransportIT.aResponseReadBeforeThePeerClosesIsDeliveredBeforeTheDisconnect` pins it.

15. **Reads are straight; exactly-once is for writes (2.6).** A query is idempotent: caching its
    reply for replay or buffering it for an absent caller spends the broker's heap on the
    largest payloads of the bus to avoid a re-execution that costs nothing. Reads bypass both,
    and the caller re-asks what it lost. Do not route a payload type with side effects as
    straight.

16. **Large answers stream with credit, never chunk (2.6).** Chunking bounds frame size, not
    memory: every hop reassembles the whole message. A stream bounds memory by the caller's
    window on every hop, because the handler produces only against credit the caller returns as
    it consumes. Ordering is per connection (`SerialLane`), not a reorder buffer: frames of one
    stream always arrive on one connection.

17. **Stream messages only to peers that negotiated them (2.6).** Decoding an unknown `@t` closes
    the channel, so `stream:1` gates every `StreamData`/`StreamCredit`/`StreamCancel`/
    `StreamRedirect`; a side that cannot stream answers whole and the other side iterates it.
    That keeps 2.5 and 2.6 peers interoperable in any mix.

18. **A local answer takes the remote path minus the network (2.6).** The short-circuit runs the
    request bytes through the same codec and handler path a remote query takes, rather than
    calling the projection with objects: tracing, interceptors and metrics see it as before, a
    view that does not serialize fails here as it would remotely, and the caller cannot share
    mutable state with the projection.

19. **Consistency tokens are per context (2.6).** A projector reads only its contexts; one global
    sequence number could make it wait for an event it will never see. The token says, per
    context, what must have been processed; the wildcard projector takes the highest.

20. **Direct streams are ticketed by the broker, and optional (2.6).** The broker keeps routing,
    locality, deadlines and authentication; it only stops carrying the data. A random,
    single-use, short-lived ticket bound to one correlation id carries its decision to the
    direct connection. A caller that cannot connect falls back to the broker on its own, so the
    feature can be enabled without knowing every caller's network.

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
| **Reconnect delivery** | `reconnectBuffer` on `BusLifecycle` buffers responses for disconnected originators (2-minute TTL, 256 MiB). `deliverPendingResponses` replays on reconnect |

None of the three applies to **straight** payload types (queries, event fetches, 2.6): they are
idempotent, so the caller asks them again instead (§4.2, decision 15).

A caller bundle can retry with the same `correlationId` and receive exactly one side-effect plus
the same response back.

Those three layers are in-memory and per server: a retry after a timeout, a
`BrokerChangedException` or a caller restart has a new `correlationId`, and after a failover it
reaches another server. **Idempotency keys (G5)** carry exactly-once across both.

| Piece | Where | Role |
|---|---|---|
| `CommandMessage.idempotencyKey` | common | Optional key on domain and service command messages. Additive field: an older peer sends none (null = the old behaviour) and skips it as unknown. Blank = none; max 255 chars |
| `CommandGateway.sendIdempotent(cmd, key[, metadata, timeout, unit])` | common | Explicit key. Refuses keys starting with `evento:` (reserved for derived keys) and keys over 255 chars. The six-argument `send(..., key)` is the new base; a gateway that does not implement keys refuses a non-null key rather than dropping it |
| `CommandGateway.supportsIdempotencyKeys()` | common | `false` by default; `true` for `CommandGatewayImpl` unless a subclass overrides the five-argument `send` without the six-argument one (a keyed send would bypass the override). Only a supporting gateway gets derived keys; any other one gets the plain five-argument send, as before G5 |
| Derived keys | bundle, `GatewayTelemetryProxy` | A consumer engine scopes the per-event gateway with `(consumerId, eventSequenceNumber)` (`DispatchContext.consumerGateway`); commands sent without a key get `evento:<consumerId>:<seq>:<CommandSimpleName>:<n>`, `n` counting the commands **of that type** in the invocation (reset on each attempt of the handler's retry loop), so a redelivered event re-issues identical keys (> 255 chars: `evento:sha256:<hex>`) |
| `es__events.idempotency_key` / `idempotency_command` + `es__events_idempotency_key_uidx` | server | Nullable key and command type (the command's identity, with `aggregate_id`), partial `UNIQUE ... WHERE idempotency_key IS NOT NULL`. Added online by `AdvisoryLockedSchemaInitializer` (columns only if missing, under a short retried `lock_timeout`; index `CONCURRENTLY`, invalid leftover dropped; every check resolves `es__events` through `search_path`, never by index name alone); `db/upgrade/es-events-idempotency-key.sql` for external schemas |
| `es__idempotency_claims` | server | One row per keyed **service** command: identity, owner, lease on the database clock. Marked `completed_at` and kept once the outcome is settled: by the append statement itself when the event is stored (same statement, same transaction), or after a handler that returned nothing. Deleted only after a failed execution, so a retry may run it |
| Keyed append | server, `EventStore` | The CPES single statement (APES: a plain insert) plus `ON CONFLICT (idempotency_key) WHERE ... DO NOTHING`; no row = a committed event holds the key (`IdempotencyKeyConflictException`) |
| Support probe | server, `EventStore.idempotencySupported()` | PostgreSQL only (another database: never, logged once); valid index on the `es__events` that `search_path` resolves, `idempotency_command` column, claims table. Otherwise keyed commands run unkeyed and the broker does no lookup, claim or lock for them (`event=idempotency_key_unsupported`, re-probed every minute). An index, column or table that disappears later (`42P10`, `42703`, `42P01`) turns support off with a WARN and the append goes through unkeyed |
| Replay / refusal | server, `CommandBrokerHandler` | Key looked up in a round trip the command makes anyway, never in one of its own: aggregate commands in the story fetch under the aggregate lock (a `UNION ALL` branch of the events query, `BrokerEventStore.fetchAggregateStory(..., key)`, `stage=with_story`); service commands in the claim statement (`claimOrFindIdempotencyKey`: a CTE reads the event and the claim and inserts the claim only when no event holds the key, `stage=before_lock`). Recorded for the same command (type + aggregate id): answered with the stored event (or an empty result for an event-less completion), no handler runs (`event=idempotent_replay stage=...`, `evento.server.commands.idempotent.replayed`). Recorded for a different command: `IdempotencyKeyMismatchException` (`event=idempotency_key_mismatch`, `evento.server.commands.idempotent.mismatched`), nothing executed, never the other command's event |

Concurrent duplicates: an aggregate command checks the key under the aggregate lock, in the same
statement that fetches the story (otherwise the duplicate's handler sees the original's event and
refuses, "already created"); without a lock, or with a lost one, the unique index lets one append
through and the others (append conflict or domain refusal) replay — the handler is pure. There is
no lookup before the lock: it cost a round trip on every first attempt to spare a replay (the
rare case) its lock wait. A service command **claims** the key in one statement that also reads
what is recorded (`INSERT ... SELECT ... WHERE NOT EXISTS (event) ON CONFLICT DO UPDATE ... WHERE`
the old claim's lease ran out and it names the same command: insert or take-over, atomic); no
re-check follows, because the owner marks its claim completed **in its append statement** and
keeps the row: the insert's arbiter reads the latest row version, so a claim can never be won for
a key whose event is stored, even one committed after the claimer's snapshot (when the owner
deleted its claim after the append, a claimer whose snapshot predated the append could insert a
fresh claim and miss the event — hence the old second lookup). A duplicate polls the claim
statement (10 ms to 200 ms backoff) holding nothing. The first lease covers the lock wait and the
forward (remaining deadline + command timeout + 10 s); it is renewed only when a lock wait without
a deadline outlasted it, and an owner that finds its claim taken over does not forward. A failed
execution deletes its claim (a retry may run). The claim is a row, not a session lock:
an earlier design took an `IDEMPOTENCY:<key>` advisory lock, pinning a lock-pool connection for
the whole handler run on top of the command's `RESOURCE:` lock, and with the pool full of such
holders each waited for a second connection none could release (hold-and-wait); since every
consumer-sent command is keyed, that was the common case. Now no command holds more than one
lock connection.

N3 interplay: a keyed command dropped at `lock_wait`/`before_forward`/`before_append` whose key is
then found stored is answered with the stored event. A keyed command that stops waiting on
another execution of its key (a service claim held past its deadline, or an aggregate lock
whose holder may be that execution) is answered `IdempotencyKeyInProgressException`
(indeterminate: the other execution may still commit; `evento.server.commands.idempotent.in_progress`),
never "expired, not executed". A `queued` drop (in `BusLifecycle`, before the broker sees the
key) executed nothing and the next retry replays.

Only successes are recorded: a command refused by its handler can be retried with the same key.
A key names one command: the broker records its type and aggregate id and refuses the key for
any other. Consequences: mint the aggregate id of a creation from the key (or once, before the
first attempt), never per attempt; a handler must not forward the key it received to its own
commands (the outer command still owns it: the nested send is refused at once, instead of
corrupting the outer result or blocking on the outer claim) and should use `key + ":" + step`;
a consumer handler whose attempts send commands of one type in a different order gets a
mismatch, not a wrong replay, and should pass explicit keys.

**Round trips (event-store statements per command, `IdempotentCommandPostgresIT`
`aKeyedFirstAttemptCostsTheRoundTripsOfAnUnkeyedOne`, counted at the JDBC driver; lock statements
are on the lock pool and identical for keyed and unkeyed commands):**

| Path | Unkeyed | Keyed, before | Keyed, now | Replay, now |
|---|---|---|---|---|
| Aggregate command | 2 (story, append) | 4 (+ lookup before the lock, + lookup under it) | **2** (key in the story query) | 1 (the story query answers) |
| Service command, with or without `lockId` | 1 (append) | 6 (+ lookup, claim, re-check, renewal, release) | **2** (+ the claim; the append completes it) | 1 (the claim statement answers) |

The remaining service-command statement is the claim itself: a write that has to happen before a
handler with side effects runs, so it cannot ride on a later round trip. A void service handler
pays one more (completing its claim, as before).

**Service handler that ran, event not stored (closed by the bundle's outcome store).** When the
append fails, or the server dies between the handler's answer and the append, the broker has no
record of the run: the claim is released (or its lease expires) and the retry forwards the
command again. The service bundle is the one party that knows the handler ran, so it keeps the
record (inbox pattern):

| Piece | Where | Role |
|---|---|---|
| `ServiceCommandOutcomeStore` (+ `StoredOutcome`, `InMemoryServiceCommandOutcomeStore`) | common, `messaging.outcome` | `find(key)`, `record(key, commandType, result)` (first wins), `recordInTransaction(Connection, ...)` (in the caller's transaction; `DuplicateServiceOutcomeException` when another execution committed first), `sweepBefore` |
| `JdbcServiceCommandOutcomeStore` | consumer-state-store-jdbc | Postgres / MySQL table `evento_v2_service_outcome` (key PK, command type, serialized result, `recorded_at`), Flyway `V2__service_command_outcome` (idempotent) |
| `EventoBundle.Builder.setServiceCommandOutcomeStore` | bundle | Off by default. With a store, `ServiceManager` answers a keyed command whose key has an outcome without running the handler (`event=service_outcome_replayed`), refuses a key recorded for another command type (`IdempotencyKeyMismatchException`), else runs the handler and records its outcome before replying |
| `ServiceCommandOutcomes` | common | Per-invocation handle, injectable into a service `@CommandHandler` and reachable as `ServiceCommandOutcomes.current()` from a `MessageHandlerInterceptor`: `idempotencyKey()`, `recordWith(Connection, result)` |

The broker needs nothing new: its retry claims the key (or takes an expired claim over),
forwards, the bundle answers from the store, and the broker appends that answer as for any
first attempt (`ServiceOutcomeReplayPostgresIT`: append failure, and server death with lease
takeover on another server; one handler run, one event each). Guarantees: recording after the
handler returns closes the broker's window, leaving the process dying between the side effect and
the record; recording **in the handler's transaction** (`recordWith`) closes that too for side
effects in the same database (both commit or neither; a racing second execution hits the
primary key, rolls back and is answered with the first outcome). Side effects outside that
database (a payment provider, a mail) need the key passed downstream
(`ServiceCommandMessage#getIdempotencyKey()` or `ServiceCommandOutcomes#idempotencyKey()`). The
store must be shared by every instance the retry can reach: in the lab, each cell's order-command
records in its own cell database, so a retry that lands in another cell is not covered. A lookup
failure fails the command (nothing ran); a record failure after a successful run only logs
(`event=service_outcome_record_failed`), since the event the broker stores answers later retries.

**Mixed-version rolling upgrade (not closed):** a server without G5 ignores the key (it skips the
unknown field and appends unkeyed). During an upgrade, a keyed retry that lands on an old server
runs as an unkeyed command: exactly-once holds only once every server runs a G5 version. Upgrade
the servers before relying on keys (bundles may follow in any order). The same holds between
G5 servers before and after the round-trip change: an older one deletes its claim after the
append instead of completing it in the append, so a newer one's claim statement can, in a window
of microseconds, miss an event that committed between its snapshot and its insert. Upgrade all
servers together, or accept that window for the duration of the roll-out.

**Keys never expire (not closed).** A key lives as long as its event (an event-less completion as
long as its claim row); there is no TTL or archival yet. Two consequences: the index and the
claims table only grow, and rewinding a consumer (resetting its checkpoint to re-process history)
re-issues the derived keys of every event it re-reads, so the commands it sends are answered as
replays instead of being executed again — right for a crash redelivery, wrong for a deliberate
re-processing. Rewind under a new consumer id (new keys) when the commands must run again.

---

## 9. Public API Surface (preserve across refactors)

These are the user-facing APIs — changing signatures requires a major version bump:

- **Annotation API**: `@Aggregate`, `@CommandHandler`, `@AggregateCommandHandler`, `@EventHandler`,
  `@EventSourcingHandler`, `@QueryHandler`, `@SagaEventHandler`, `@InvocationHandler`,
  `@Service`, `@Projector`, `@Projection`, `@Saga`, `@Observer`, `@Invoker`
- **Bundle bootstrap**: `EventoBundle.Builder` (fluent builder returned by `EventoBundle.builder()`)
- **Modeling types**: `AggregateState`, `Event`, `Command`, `Query`, `DomainCommandMessage`,
  `DomainEventMessage`, `ServiceCommandMessage`, `ServiceEventMessage`, `DecoratedDomainCommandMessage`
- **Gateway API**: `CommandGateway` (incl. `sendIdempotent`, G5; `sendTracked` → `Tracked<R>`, 2.6),
  `QueryGateway` (injected into components; `queryWith(query, QueryOptions)`, `stream(query[, options])`
  → `QueryStream<V>`, 2.6)
- **Query model (2.6)**: `Single`, `Multiple`, `Page` + `PageRequest`, `Streamed`; `ConsistencyToken`,
  `QueryOptions`, `ConsistencyTimeoutException`, `QueryStream.QueryStreamException`
- **Bundle builder options (2.6)**: `setLocalQueryShortCircuit`, `setCompressionThreshold`,
  `setAcceptCompressedReplies`, `setDirectStreamConfig(DirectStreamConfig)`, `setStreamFrameBytes`
- **Service outcome SPI** (G5 inbox): `ServiceCommandOutcomeStore`, `ServiceCommandOutcomes`
  (injectable into service handlers), `EventoBundle.Builder.setServiceCommandOutcomeStore`
- **Consumer SPIs**: `ConsumerStateStore`, `ConsumerLock`, `SagaStateStore`, `DeadEventQueue`,
  `DedupeStore`, `ConsumerEngineConfig`, `ConsumerExecutor` + `ConsumerExecutors`
- **Server Spring starter**: `evento.server.bus.*` properties + `BusFacade` autowiring

---

## 10. Build & Test

**Toolchain:** JDK 25.0.3 or later (`/usr/libexec/java_home -v 25` on macOS;
`C:/Users/Gaborando/.jdks/jdk-25.0.4.1+1` on the Windows dev host). Never 25.0.1/25.0.2
(JDK-8370887 stalls virtual-thread timed waits ~20 s). Gradle 9.7.1 wrapper at `./gradlew`.

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

Other gates: `:evento-transport-netty:netty41Test` (Netty 4.1 compatibility, part of `check`);
`EVENTO_RUN_JDBC_IT=true` for the Testcontainers/Postgres ITs (e.g. `MultiServerCatalogIT`,
`IdempotentCommandPostgresIT`, `ServiceOutcomeReplayPostgresIT`); `EVENTO_RUN_BENCH=true` for `QueryPathBenchmarkIT` (broker cost of a query answer by size and mode, `evento-lab-cluster/docs/query-path.md`) and
`CommandLatencyBenchmarkIT`; `EVENTO_LAB_CLUSTER=true` for the `evento-lab-cluster` scenarios
(run through its `scripts/`, needs the Docker stack).

**Coverage:** `./gradlew jacocoTestReport` — minimum 70% on new modules.

**Test counts (JDK 25.0.4, 2.5.2, standard run above):**
- `evento-transport-api`: 53
- `evento-transport-netty`: 18 (also run against Netty 4.1 by `netty41Test`)
- `evento-server`: ~228 on CI's core run (≈54 skipped without Postgres/Docker)
- `evento-common`: 131
- `evento-bundle`: 83
- `evento-lab`: 45 (4 skipped)
- `evento-lab-ms-it`: 25
- `evento-consumer-state-store-jdbc`: 74 JDBC ITs, all skipped unless `EVENTO_RUN_JDBC_IT=true`

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

Tables created by the Flyway migrations (V1, V2) per dialect (`postgres` / `mysql`):

| Table | Purpose |
|---|---|
| `evento_v2_consumer_state` | Checkpoint + version + enabled flag + error history |
| `evento_v2_saga_state` | Saga state JSON(B) + flat `associations` JSON(B) column for fast lookup |
| `evento_v2_dead_event` | DLQ entries with retry flag |
| `evento_v2_dedupe` | Observer dedupe entries with sweep window |
| `evento_v2_service_outcome` | (V2) Outcomes of keyed service commands, kept by the service bundle (`JdbcServiceCommandOutcomeStore`, §8): key PK, command type, serialized result, `recorded_at` |

JDBC locks:
- **Postgres:** `pg_try_advisory_lock(hashtext(consumerId))` — session-scoped, pins one `Connection` per `LockHandle`
- **MySQL:** `GET_LOCK(consumerId, 0)` — same pattern

**Operational sizing (important).** Because each held `LockHandle` pins a dedicated pooled
`Connection` for its whole lifetime, the HikariCP pool must be sized for **at least one connection
per concurrently-running consumer** (projector/saga/observer engine), plus headroom for the normal
query/checkpoint traffic. **Async consumers add a second term:** every concurrently-executing
handler that opens a transaction (typically via a `MessageHandlerInterceptor`) holds a
connection for its whole task, so the pool needs
`concurrent consumers + Σ(capacity of each ConsumerExecutor used by transactional handlers) + headroom`.
Cap a transactional handler's executor at its share of the pool — an executor of capacity 64
against a 10-connection pool surfaces as acquisition timeouts, which the consumer classifies
as transient and (with the default `retry`) dead-letters immediately.

Virtual threads are fine for transactional handlers: `VirtualThreadJdbcConcurrencyIT`
measures 32 concurrent `pg_sleep` transactions reaching full capacity on an 8-core box
(303 ms vs 8 s serial), so no carrier pinning. Note instead that **a cold Hikari pool, not
the executor, throttles a freshly started async consumer** — connections open lazily and
cost more than a short handler, so raise `minimumIdle` if catch-up burst latency matters. Under-sizing manifests as connection-acquisition timeouts, not lock
errors. `LockHandle` is `AutoCloseable` and idempotent on `close()`; always release it in a
try-with-resources / `finally` so a failed consumer cycle doesn't leak its pinned connection. The
SQL-injection-hardened `PgDistributedLock` (server-side command lock) is a separate JVM-or-Postgres
lock and does not consume a pool connection per held lock.

**Request capacity (the other sizing axis).** The consume path is bounded by consumer
executors; the *request* path is bounded by the bus business executor. Three limits sit in
series and a request is capped by the smallest: `evento.server.bus.business-executor-max-size`
(default `cores × 8`), `…-queue-capacity` (default 256), and `evento.es.fetch.concurrency`
(default **4**, a fair semaphore in `EventStoreRequestHandlerBridge` — usually the binding
constraint on a consumer-heavy cluster, since no number of bus threads raises it).

Two properties of this path drive its failure mode. First, `BusBusinessExecutor` grows to
`max` **before** it queues — a stock `ThreadPoolExecutor` does the reverse and would run on
`core` threads until the queue filled, making `max` unreachable under exactly the load it
exists for. Second, every request carries a client deadline (30s by default) and **nothing
cancels the work when it expires**. Past capacity the queue therefore fills with requests
whose callers have already gone, and the server spends its threads producing responses
nobody reads: throughput collapses rather than degrading, with idle-looking CPU and
databases. Retries sustain it, so it does not self-heal when the triggering load stops —
reducing *client* concurrency is what breaks it, and fewer concurrent clients can complete
strictly more work.

The queue is therefore the buffer behind a fully-grown pool, not a shock absorber: deeper
than `deadline ÷ service time` is dead work by construction, which is why the default is
small. Enlarging it to absorb load makes the collapse worse.

Detect it with `evento.server.bus.executor.saturated` (alert on any sustained increase — it
only moves when the pool is at max *and* the queue is full), `…queue.depth` as the earlier
warning, and the `WARN` lines `event=bus_business_executor_saturated` and
`event=bundle_correlation_expired … byType={...}`. Bundles can bound themselves with
`BundleClientConfig.Builder.maxInFlightRequests(n)`. See §14 and the public docs page
*Evento Server → SetUp Evento Server → Throughput and Capacity*.

---

## 13. Server REST API Surface

All controllers are under `com.evento.server.web.*` and depend on `BusFacade`.

| Controller | Prefix | Key endpoints |
|---|---|---|
| `DashboardController` | `/api/dashboard` | Cluster status snapshot |
| `ClusterStatusController` | `/api/cluster` | Connected nodes, availability |
| `BundleController` | `/api/bundle` | Bundle list, update, delete |
| `HandlerController` | `/api/handler` | Handler graph, invocation graph |
| `CatalogController` | `/api/catalog` | Payload and component catalog; components by `componentId` (`bundle:name`), bare name only when unambiguous |
| `ConsumerController` | `/api/consumer` | Consumer status, dead queue, retry |
| `PerformanceController` | `/api/performance` | Performance model, time series |
| `ArtifactController` | `/api/artifact` | Bundle artifact upload |
| `FlowsController` | `/api/flows` | Handler flow visualization |
| `SystemStateStoreController` | `/api/system-state-store` | Aggregate snapshots |

Auth is **HTTP Basic** against the static Spring Boot in-memory user
(`spring.security.user.name/password/roles=WEB,ADMIN` in `application.properties`,
env-overridable); `WebConfig` requires auth on `/api/**` + actuator (except
health/info) and answers plain `401` without `WWW-Authenticate` so the GUI's own
login page handles failures. Controller `@Secured("ROLE_WEB"/"ROLE_ADMIN")`
annotations are the per-endpoint enforcement. (The former JWT stack —
`AuthFilter`/`AuthService`/`TokenRole`/`AuthController` — was removed with the
explorative read-only pivot.)

---

## 14. Observability

- **Micrometer metrics** — wired via `BusMetricsBinder` (`com.evento.server.bus.spring`),
  exposed on `/actuator/prometheus` + `/actuator/metrics` (`micrometer-registry-prometheus`):
  - `evento.server.connections` — connected bundle nodes
  - `evento.server.connections.available` — nodes able to receive
  - `evento.server.correlations.pending` — outstanding server-initiated correlations
  - `evento.server.forwarding.table.size` — in-flight relayed requests
  - `evento.server.forwarded{path=raw|reencoded}` — zero-copy vs re-encoded forward counts
  - `evento.server.requests.expired{stage=queued|lock_wait|before_forward|before_append}` —
    requests dropped unexecuted because the caller's deadline had passed (§4.6)
  - `evento.server.commands.idempotent.replayed` — keyed commands answered with the event stored
    under their idempotency key instead of being executed (§8; bound by `CommandBrokerHandler`)
  - `evento.server.commands.idempotent.mismatched` — keyed commands refused because their key is
    recorded for a different command (type or aggregate id)
  - `evento.server.commands.idempotent.in_progress` — keyed commands answered "in progress"
    (indeterminate) because another execution of their key did not end before they stopped waiting
  - `evento.server.command.phase{phase,keyed}` — timer per phase of every *executed* aggregate
    command (event appended; replays, refusals and drops are not sampled), from the frame's
    decode on the I/O thread to the reply written to the caller's socket. Phases (consecutive
    `nanoTime` laps, so they add up to `total`): `queue`, `codec` (broker CBOR: request decode,
    forward encode, answer decode, reply body encode), `lock` (lock-pool borrow +
    `pg_try_advisory_lock`), `story` (snapshot when not cached + events; a keyed command's key
    check rides on it), `forward` (to the aggregate bundle and back), `verify` (`verifyHeld`),
    `append`, `snapshot` (only when one is saved), `unlock`, `reply`, `total`. Percentile
    histogram (0.1 ms–30 s, ~85 buckets per timer; p50/p95/p99 via `histogram_quantile`). Wiring:
    `CommandPhaseMetrics` (bound by `CommandBrokerHandler.bindTo`), fed by
    `BusLifecycle.RequestTiming` (arrival / dispatch / reply-written marks, handed to
    `TimedRequestHandler`s). Raw samples: `CommandPhaseMetrics.addListener`; one DEBUG line per
    command `event=command_phases ... lock=…ms story=…ms …` on logger `com.evento.server.es.phases`
    (`LOGGING_LEVEL_COM_EVENTO_SERVER_ES_PHASES=DEBUG`). Measured breakdown (local and WAN):
    `evento-lab-cluster/docs/command-latency.md`.
  - *Not yet covered:* heartbeat lag, service-command and query phase timers, consumer lag (future work).
- **Bundle-side latencies** (bundles have no Micrometer): `BundleClient.latencies()` /
  `EventoBundle.getLatencies()` → `BundleLatencies` (`com.evento.application.client.metrics`):
  `requestRtt()` by payload type (request frame handed to the transport → answer frame decoded,
  non-error answers only) and `handlers()` by handler type `aggregate|service|query` (handler run,
  decode excluded) plus `<type>.decode` / `<type>.encode`, recorded by `BundleInboundDispatcher`.
  `LatencyHistogram`: lock-free log-linear buckets (32 per power of two, ≤ ~3% percentile
  error, ~15 KB each), `snapshot()` → count/mean/p50/p95/p99/max, `reset()` starts a window. The
  lab publishes them on `GET /lab/latency`.
- **Parallel-consumption metrics** — wired via `ConsumerMetricsRegistry`
  (`com.evento.server.bus.spring`), fed by a periodic `ConsumerStatsMessage` bundles push on
  the admin notification channel. Push rather than poll: the counters live in bundle-side
  objects, and polling per scrape would make one scrape a round-trip per consumer.
  - `evento.consumer.executor.{capacity,in.flight,admitted,rejected,completed,failed}`
    — tagged `bundle,instance,executor`. **`rejected` is the alerting signal**: the executor
    reporting it is the bottleneck.
  - `evento.consumer.async.{in.flight,submit.timeouts,transient.failures}`
    — tagged `bundle,instance,consumer,component`.
  - Meters are removed on `BusEvent.NodeLeft`, so rolling restarts do not leak a series per
    dead instance. Bundles with no consumer executor push nothing at all.
- **Health** — `BusHealthIndicator` reports the bus bound-port + node counts on
  `/actuator/health`; liveness/readiness probes enabled. DataSource health via Actuator's
  built-in indicator.
- **Performance store**: `HandlerInvocationCountPerformance` + `HandlerServiceTimePerformance`
  persisted by `PerformanceStoreService`, consumed via `PerformanceController`.
- **Structured logging:** SLF4J. Key=value on every bus lifecycle transition. `event=disconnect_superseded_skip` etc.
- **Zero-copy counters**: `BusLifecycle.forwardedRawCount` / `forwardedReencodedCount` — every
  Netty-to-Netty forward must use the raw path; tests pin this contract.
- **Query path (2.6)**: broker `evento.server.streams.active`, `evento.server.streams.opened`,
  `evento.server.stream.frames`, `evento.server.stream.bytes`,
  `evento.server.reconnect.buffer.bytes`, `evento.server.dedup.retained.bytes` (Micrometer, bound
  by `BusMetricsBinder`); bundle `BundleClient.localQueries()`, `inboundStreams()`,
  `outboundStreams()`, `directStreamsServed()` / `directStreamsFollowed()` /
  `directStreamFallbacks()`, handler latencies `query.stream`. Logs: `event=query_answered_locally`,
  `query_streamed`, `query_waited_for_writes` (DEBUG), `idempotent_requests_resent`,
  `direct_stream_fallback`, `direct_stream_refused`, `straight_response_dropped_originator_away`.

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
- **Wire DTOs evolve by addition, and every codec tolerates unknown properties.** All four
  mappers (`JacksonCborCodec`, `JacksonCborPayloadCodec`, `AdminPayloadCodec`,
  `ObjectMapperUtils`) disable `FAIL_ON_UNKNOWN_PROPERTIES`, so a peer one version ahead
  does not have its payload rejected. Records normalise `null` to a default in their
  `@JsonCreator`, covering the opposite skew. Keep both properties when adding a field:
  a new record component needs a `null`-normalising creator.
  *History:* the transport codecs used to be strict, and a derived `isAsync()` getter on
  `RegisteredHandler` therefore made Jackson emit an `async` property that the broker
  rejected — killing the **entire** `BundleDiscoveryInfo`. The failure was near-invisible:
  the bundle registered, enabled and consumed normally while all its handler metadata
  vanished from the dashboard (`event=listener_error … decode failed for
  BundleDiscoveryInfo`). Strictness there was never a security control — the
  deserialization defence is `MessageTypeRegistry` / the `PolymorphicTypeValidator`, which
  bound *which types* may be instantiated; skipping an unrecognised property on an
  already-whitelisted type instantiates nothing. `CodecVersionToleranceTest` pins both
  skew directions.
- **Virtual threads** for business executor (`Executors.newVirtualThreadPerTaskExecutor()`). Never
  block the Netty EventLoop (CBOR decode, handler dispatch all go to the business executor).

---

## 16. Current Status

Version: `2.5.2` (tag `v2.5.2`), published to Maven Central, GitHub Packages, Docker Hub / GHCR
(`eventoframework/evento-server:2.5.2` = `latest`). Releases are cut from `main` with
`scripts/release.sh patch|minor|major`; the tag triggers every publishing workflow.

| Line | What it brought |
|---|---|
| 2.0.0 | CBOR/Netty transport, sealed messages, v2 consumer engines, exactly-once QoS caches |
| 2.4.x | parallel consumers, bounded observer fan-out, throughput/capacity metrics, WATERMARK checkpoints |
| 2.5.0 | cluster mode: endpoint failover + fail-back, idempotency keys and service outcome store, caller deadlines, locality-preferred routing, presence, per-bundle catalog, bounded distributed locks, per-phase command latency |
| 2.5.1 | dependency sweep (Spring Boot 4.1.1, Netty 4.2.17, Jackson 2.22.2, Gradle 9.7.1, Angular 22.2) |
| 2.5.2 | replies no longer lost when their sender closes right after writing them (§6 decision 14) |

Details per release: `CHANGELOG.md`. Session-level progress and open work: [`STATUS.md`](STATUS.md);
open cluster limits and roadmap: `evento-lab-cluster/docs/L5-limits-roadmap.md`. User docs:
docs.eventoframework.com (repo `evento-doc`), cluster section under *Evento Server Cluster*.
