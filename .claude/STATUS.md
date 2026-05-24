# Evento v2.0 rewrite — status snapshot

Last updated after evento-bundle SOLID/DX refactor.
Snapshot lives in the repo so the work can resume from another machine
without losing context. Companion docs in this folder:

- [`PLAN.md`](PLAN.md) — the approved, full v2.0 rewrite plan (3 PRs)
- `STATUS.md` (this file) — what's done, what's next

## Branch

All work is on **`next`**, branched off `main`. Do not push to `main`;
`next` will be promoted to `main` as `v2.0.0` only after PR3 ships and a
1–2 week staging soak.

## What's done (25 commits + 1 uncommitted refactor beyond `main`)

| # | Commit | What landed |
|---|---|---|
| 26 | _(uncommitted)_ | `refactor(bundle)`: SOLID/DX cleanup of `evento-bundle`. **`MessageHandlerInterceptor`** — all 24 methods converted from abstract to `default` (ISP fix): void methods get empty bodies, return-type methods pass through the last parameter, exception-returning methods return the throwable. Implementors now override only the hooks they need. **`LogTracesMessageHandlerInterceptor`** — fixed 4 swapped before/after log messages for Saga and Observer handlers. **`ProjectionReference.invoke()`** — fixed silent bug: all three interceptor calls passed `getRef().getClass()` (a `Class<?>`) instead of `getRef()` (the actual projection instance). **`ReflectionUtils.invoke()`** — propagates checked exceptions directly via `throw e.getTargetException()` instead of wrapping in `RuntimeException`; signature changed to `throws Throwable`. **`DispatchContext` record** (new) — groups `TracingAgent`, `BiFunction<String,Message<?>,GatewayTelemetryProxy>`, `MessageHandlerInterceptor` into one object; reduces `ProjectorEngine`/`SagaEngine`/`ObserverEngine` constructor from 15-16 params to 13-14. **`HandlerMetadataBuilder`** (new, package-private) — extracts the 115-line handler/payloadInfo loop from `EventoBundle.start()` into a focused class with `build(managers…) → Result(handlers, payloadInfo)`. **`EventoBundle`** — `startProjectorEnginesV2` and `startSagaAndObserverEnginesV2` moved from instance methods to `private static` methods on the nested `Builder` class; `EventoBundle` is now a pure runtime object. `start()` simplified with `HandlerMetadataBuilder` call and `DispatchContext` creation. **Tests updated** — `EngineSupervisorTest` and `EngineHandleAdminTest` updated to use `DispatchContext`-based constructors. All 45 (bundle + lab + ms-it) tests green. |
| 25 | `a9f39145` | `fix(common): remove 20 MB CBOR string cap and eliminate SerializedQueryResponse from transport`. Two independent improvements shipped together. **StreamConstraints fix:** `AdminPayloadCodec.defaultMapper()` now builds `CBORFactory` via `CBORFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build()).build()` instead of `new CBORFactory()`. Jackson 2.15+ defaults to a 20 MB string-length cap on CBOR text tokens; any query response whose serialized `serializedObject` field exceeded that limit threw `StreamConstraintsException`. The fix is at the factory level and covers all codec instances. **SerializedQueryResponse eliminated from transport (Option C):** `ProjectionManager.handle()` return type changed from `SerializedQueryResponse<?>` to `QueryResponse<?>` — the result is now placed directly in `EventoResponse.body` by `BundleInboundDispatcher` (which already returns `Serializable`). `QueryGatewayImpl.query()` casts the response directly with `(T) r` instead of unwrapping a `SerializedQueryResponse`. `SerializedQueryResponse` itself is retained as an empty stub for backward-compat deserialization of any old wire format still in flight. `ObjectMapperUtils.java` had 8 unused imports removed. **Memory profile:** peak heap for an 80 MB query response dropped from ~400 MB to ~240 MB (~40% reduction) by eliminating one UTF-16 `String` copy (the `serializedObject` field) and one JSON serialization step. **Regression test:** `BundleAdminRoundTripIT.largeResponseBody_decodesWithoutStreamConstraintError` — a `LargeBody("X".repeat(80_000_000))` round-trips over real Netty TCP and decodes cleanly in under 60 s. **Build:** `evento-server/build.gradle` test task now carries `jvmArgs '-Xmx2g'` so large-payload ITs don't OOM the Gradle test JVM. **7 files changed:** `AdminPayloadCodec.java`, `ProjectionManager.java`, `QueryGatewayImpl.java`, `SerializedQueryResponse.java`, `ObjectMapperUtils.java`, `evento-server/build.gradle`, `BundleAdminRoundTripIT.java`. |
| 24 | `44b3ffae` | `feat(transport/netty): transparent chunking layer — no message size limit`. Two new Netty pipeline handlers inserted between the length-field codec pair and the CBOR codec pair. **`ChunkingEncoder`** (outbound): wraps every outbound `ByteBuf` — from both `send(Message)` and `sendRaw(byte[])` — as a FULL frame (`0x00` + CBOR, when it fits) or one-or-more CHUNK frames (`0x01` + 16-byte stream UUID + 1-byte isLast + data). **`ChunkReassembler`** (inbound): strips the discriminator; FULL frames pass through with a `retain()`; CHUNK frames are accumulated per stream UUID in a `ByteArrayOutputStream` and fired downstream only when the last chunk arrives. `maxFrameLength` now controls per-chunk memory pressure, not message size — there is no longer any hard limit on individual message size. The `-22` chunk-data capacity formula accounts for 18 bytes of chunk header plus the `+4` that `LengthFieldBasedFrameDecoder` adds internally to the raw length field when checking against `maxFrameLength`. New `largePayloadTransparentlyChunked` IT: dedicated 64 KB chunk-size pair exchanges a 5 MB payload (~80 chunks), verifies byte-for-byte equality. |
| 23 | `251be4f2` | `feat(transport): split bundle-registration into lean + discovery frames`. Fixes `TooLongFrameException` (34 MB bundle-registration frame exceeding the 16 MB Netty cap). The `evento:bundle-registration` notification is now lean-only (`bundleVersion` + `handlerPayloadTypes`). Rich auto-discovery metadata (`handlers` + `payloadInfo` schemas) is carried by a new `evento:bundle-discovery` notification sent after `evento:enable`. **New types:** `BundleDiscoveryInfo` record + `ProtocolNotifications.BUNDLE_DISCOVERY`. **New bus event:** `BusEvent.BundleDiscovered` added to the sealed hierarchy; `CommandBrokerHandler` and `AutoDiscoveryService` both switch from `BundleRegistered` to `BundleDiscovered`. **`ConnectionSupervisor.performRegistration`** split into 3 steps: lean registration → enable → discovery. `BundleRegistrationInfo.lean()` factory kept for backward-compatible test callsites. |
| 22 | _(prev slice)_ | `feat(evento-lab-ms): complex RECQ scenarios — full order lifecycle, payment saga, notification service, multi-context consumers, RTT stress tests`. **API expansion:** 5 new commands (`AddOrderItemCommand`, `RemoveOrderItemCommand`, `CompleteOrderCommand`, `SendNotificationCommand`, `OpenPaymentIntentCommand`), 6 new events (`OrderItemAddedEvent`, `OrderItemRemovedEvent`, `OrderCompletedEvent`, `NotificationSentEvent`, `PaymentIntentOpenedEvent`, `PaymentStatusChangedEvent`), new `OrderItemView`, enriched `OrderView` (items, paymentStatus, context). **Command module:** `OrderAggregate` gets full lifecycle (create + add item + remove item), `OrderAggregateState` extended with items list + context + status, `OrderService` extended with `CompleteOrderCommand`, new `PaymentService` handling `OpenPaymentIntentCommand → PaymentIntentOpenedEvent`. **Notification Service (RECQ Service):** `NotificationService` in observer bundle handles `SendNotificationCommand → NotificationSentEvent`, logs to `MsNotificationLog`; `OrderObserver` now injects `CommandGateway` and dispatches `SendNotificationCommand` on every order event. **Payment Saga:** `OrderSaga` rewritten to implement the full payment flow — opens on `OrderCompletedEvent`, generates a `paymentIntentId`, sends `OpenPaymentIntentCommand` via `CommandGateway`, then handles `PaymentStatusChangedEvent`: SUCCESS → `ConfirmOrderCommand`, FAILED → `CancelOrderCommand`. **Multi-context consumers:** `MsHarness.withQueryBundleForContext(ctx)` starts a projector bundle for a geographic context ("IT" or "UK") using `setComponentContexts(OrderProjector.class, ctx)`; `MsTestEventStore` extended with `publishWithContext()` and context-aware `EventFetchRequest` filtering. **Framework fix:** `CommandGatewayImpl` updated to handle the case where the v2 CBOR wire adapter returns an already-deserialized `Serializable` instead of a JSON `String` — fixes `JsonParseException` when sagas send service commands. **+12 new IT tests** (19 total, 0 failures): `MsOrderLifecycleIT` (2), `MsPaymentSagaIT` (2), `MsNotificationIT` (3), `MsMultiContextIT` (2), `MsRttIT` (3); `MsSagaIT` updated to test payment flow. |
| 21 | _(prev slice)_ | `feat(evento-lab): api-package refactor + evento-lab-microservices multi-bundle IT harness`. Two changes in one commit. **api-package refactor:** payload types (`CreateOrderCommand`, `ConfirmOrderCommand`, `CancelOrderCommand`, `OrderCreatedEvent`, `OrderConfirmedEvent`, `OrderCancelledEvent`, `FindOrderByIdQuery`, `ListOrdersQuery`, `OrderView`) moved from flat `com.evento.lab.{command,event,query,view}` packages into `com.evento.lab.api.{command,event,query,view}` — matching the `evento-demo-api` convention. Old packages deleted; all bundle source files + lab IT tests updated to new imports. **evento-lab-microservices:** new multi-module project (`settings.gradle` adds 6 modules). Six modules: `evento-lab-ms-api` (shared payload types under `com.evento.lab.ms.api.*`), `evento-lab-ms-command` (Spring Boot; `OrderAggregate` + `OrderService`), `evento-lab-ms-query` (Spring Boot; `OrderProjector` + `OrderProjection` + `OrderViewStore`), `evento-lab-ms-saga` (Spring Boot; `OrderSaga` + `MsSagaStore`), `evento-lab-ms-observer` (Spring Boot; `OrderObserver` + `MsObservedEvents`), `evento-lab-ms-it` (IT harness). `MsHarness` starts multiple `EventoBundle` instances against a shared `MsEmbeddedBroker`; `MsTestEventStore` handles `EventFetchRequest`/`EventLastSequenceNumberRequest` and adds `publishServiceEvent()` for `ServiceEvent`-derived events. Static singleton stores (`OrderViewStore`, `MsSagaStore`, `MsObservedEvents`) serve as cross-bundle assertion surfaces in the same JVM. **+7 new IT tests**: `MsConsumerLifecycleIT` (3 — multi-bundle fan-out, multi-event projection, observer+projector independence), `MsSagaIT` (2 — happy-path CREATED→CONFIRMED, compensation CREATED→CANCELLED), `MsReconnectIT` (2 — bundle reconnects after broker drop, bundle survives broker close). |
| 20 | `7ee2738b` | `feat(evento-lab)`: new end-to-end integration test module. `EmbeddedBroker` spins up a real v2 broker on an ephemeral port; `TestEventStoreBundleClient` acts as an in-process event journal, handling `EventFetchRequest` + `EventLastSequenceNumberRequest`. Lab domain: `CreateOrderCommand/ConfirmOrderCommand/CancelOrderCommand`, `OrderCreated/Confirmed/CancelledEvent`, `FindOrderByIdQuery/ListOrdersQuery`, `OrderView`, `LabAggregate`, `LabService`, `LabProjector` (projector), `LabObserver` (observer), `LabSaga` (saga), `LabProjection` (query handler). `LabStore` is a thread-safe in-memory singleton for cross-component assertions. **+6 tests** across `ConnectivityIT` (3) and `InMemoryConsumerIT` (3). `AbstractJdbcConsumerIT` + `PostgresConsumerIT` + `MysqlConsumerIT` provide JDBC-backed variants (gated on Docker). `settings.gradle` adds `include 'evento-lab'`. |
| 17 | _(prev slice)_ | `feat(v2-cleanup)`: PR3.5 — v1 code deleted. Removed: `MessageBus.java` (1 099 lines), `MessageBusFacade`, the four v1 bundle-side `EventConsumer` subclasses (`ProjectorEvenConsumer`, `SagaEventConsumer`, `ObserverEventConsumer`, `EventConsumer`), the v1 `ConsumerStateStore` abstract class + `InMemoryConsumerStateStore` (v1), `StoredSagaState` (restored — still used by v2 `SagaStateStore` SPI), the 5 `internal/` wire types (`ClientHeartBeatMessage`, `ServerHeartBeatMessage`, `Correlation`, `EnableMessage`, `DisableMessage`), and `internal/discovery/BundleRegistered` + `BundleRegistration`. Deleted `evento-consumer-state-store-postgres` and `evento-consumer-state-store-mysql` module directories (replaced by `jdbc-v2`). Removed from `settings.gradle`. `BusFacadeConfiguration` is now unconditional (v2 always active). `BusV2Configuration` has `@ConditionalOnProperty` removed. `ConsumerComponentManager` removes the `isShuttingDown` supplier and its parameter chain. `ProjectorManager`, `SagaManager`, `ObserverManager` remove the v1 `startEventConsumers` / `startSagaEventConsumers` / `startObserverEventConsumers` methods. `ConsumerEngineConfig` gains the static `inMemory(EventoServer, PerformanceService)` factory. `EventoBundle.Builder` drops `consumerStateStoreBuilder`; v2 path is now the only path (defaults to `ConsumerEngineConfig::inMemory` when not configured). Shutdown hook changed from `isShuttingDown.set(true)` to `eventoBundle.get().stopV2Engines(Duration.ofSeconds(30))`. `sendConsumerRegistration(v1)` static method deleted. `getEventConsumer()` simplified — always delegates to `engineSupervisor`. Demo configs (`evento-demo-saga`, `evento-demo-query`) migrated from `setConsumerStateStoreBuilder` to `setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)`. `BundleAdminRoundTripIT` migrated from `extends EventConsumer` to `implements ConsumerHandle`. Two restored files: `StoredSagaState.java` (still needed by v2 SPI), `Expirable.java` (interface still implemented by `EventoRequest`/`EventoResponse`). |
| 16 | _(this slice)_ | `feat(consumer-v2)`: PR3.4 — v2 engines wired on top of `ConsumerProcessor`. New package `evento-bundle/.../consumer/v2/` with `ProjectorEngine`, `SagaEngine`, `ObserverEngine` (line-by-line ports of the v1 engines, swapping the consumer source from the v1 `ConsumerStateStore` abstract class to the v2 `ConsumerProcessor` + SPI composition) and `EngineSupervisor` (virtual-thread executor with bounded `shutdown → awaitTermination → shutdownNow` deadline). New shared `ConsumerHandle` interface in `consumer/` is the admin surface for both v1 `EventConsumer` (retrofitted to implement it — additive change) and the v2 engines; `BundleAdminRequestHandler.ConsumerLookup` now returns `Optional<? extends ConsumerHandle>` so the dashboard / discovery round-trip works on either runtime path. `EventoBundle.Builder` grows one new opt-in field `consumerEngineConfigBuilder: BiFunction<EventoServer, PerformanceService, ConsumerEngineConfig>`; when set, `start()` constructs an `EngineSupervisor` instead of spawning platform threads on the v1 managers, preserving the two-phase startup (projector-head-reached gate, then enable, then saga/observer). v1 path is the unchanged default and remains the only Spring-wired runtime today. **Structured-concurrency note**: PLAN.md called for `StructuredTaskScope.ShutdownOnFailure`; on JDK 25 the stable scope API requires the owner thread to close it, which doesn't fit long-running engines that outlive any single method. We chose a virtual-thread executor + deadlined shutdown to deliver the same load-bearing property (no orphan threads, bounded stop). Engines wanting bounded fan-out within a single batch are free to open a scope inside their own `run()`. **+7 tests** in `evento-bundle` (`EngineSupervisorTest` — lifecycle, idempotent stop, `findConsumer` routing, `shutdownSupplier`; `EngineHandleAdminTest` — `ConsumerHandle` delegate methods for projector/saga/observer). Gradle module gained `junit-platform-launcher` testRuntimeOnly to play nicely with Gradle 9's test executor. **v1 consumer engines + `ConsumerStateStore` abstract class still untouched** — slice 3.5 deletes them. |
| 15 | `9fcac3a8` | `feat(consumer-v2)`: PR3.3 — v2 consumer state SPI covering the full v1 surface (lock + consume loop + saga state + DLQ + control + checkpoint). Replaces the monolithic v1 `ConsumerStateStore` abstract class with one concrete consume-loop class (`ConsumerProcessor`) composed on five focused persistence SPIs. **`evento-common/.../messaging/consumer/v2/`:** `ConsumerProcessor` (consumeEventsForProjector/Observer/Saga + their dead-event variants + `handleLastError` + `toConsumerStatus(ConsumerFetchStatusResponseMessage)` + `getLastEventSequenceNumberSagaOrHead` — direct port of the v1 loops). SPIs: `ConsumerLock` (try-acquire returns `LockHandle: AutoCloseable`), `ConsumerStateStore` (checkpoint with optimistic versioning + enable/disable + error history), `SagaStateStore` (instance lookup by association + insert/update/delete), `DeadEventQueue` (per-consumer DLQ), `DedupeStore` (observer dedupe). Value types: sealed `ConsumerCheckpoint` + `EventCheckpoint`/`SagaCheckpoint`/`ProjectorCheckpoint` records, `ConsumerErrorState` record, `OptimisticLockException`, `VersionedCheckpoint`. **InMemory impls** for all five SPIs in `.impl/`. **+52 unit tests** in evento-common (`ConsumerProcessorTest` 18, `InMemoryConsumerStateStoreTest` 13, plus lock/saga/dlq/dedupe). One minor v1 change: `SagaState.getAssociations()` accessor added (additive — v1 callers untouched) so v2 stores can persist the flat association map. **New module `evento-consumer-state-store-jdbc-v2`** with `JdbcConsumerStateStore` (full surface: checkpoint + enable + error), `JdbcConsumerLock` (Postgres `pg_try_advisory_lock(hashtext(id))` / MySQL `GET_LOCK(id, 0)` — both pin a session-scoped Connection per handle), `JdbcSagaStateStore` (JSONB on Postgres / JSON on MySQL with flat `associations` JSON column for fast lookup via `->> ?` / `JSON_EXTRACT`), `JdbcDeadEventQueue`, `JdbcDedupeStore`, `SqlDialect` enum, `FlywayMigrator`. Schema (single V1 migration per dialect): `evento_v2_consumer_state` + `evento_v2_saga_state` + `evento_v2_dead_event` + `evento_v2_dedupe`. Testcontainers IT covering the full surface (one abstract IT + Postgres + MySQL subclasses, 23 scenarios each) gated on `EVENTO_RUN_JDBC_IT=true` so the suite stays green on machines where Docker Desktop's `/info` endpoint mis-reports (Docker Desktop 29.x on macOS in this case). Versions added: Flyway 11.7.2, Testcontainers 1.21.3, Postgres JDBC 42.7.4, MySQL connector 9.1.0, Hikari 6.2.1. **v1 `ConsumerStateStore` abstract class still untouched.** Engines wiring (3.4) and v1 deletion (3.5) still pending. |
| 1 | `a6f7eab3` | `chore(build)`: Java 21 → 25, Gradle 8.5 → 9.0, Spring Boot 3.2 → 3.5.5, Jackson 2.15 → 2.18.2 (+ CBOR), Lombok 1.18.40, JUnit 5.11.4, AssertJ. Centralized versions in `build.gradle` `allprojects { ext { … } }`. |
| 2 | `0038672c` | `feat(transport-api)`: new wire SPI module — sealed `Message` records (Hello/Welcome/Reject/Ping/Pong/Request/Response/Notification), `Codec`, `PayloadCodec`, `Transport`, `TransportServer`, `ConnectionStateMachine`, `ExponentialBackoffWithJitter`, `MessageDispatcher`, `MessageTypeRegistry`, `InMemoryTransport`. **45 tests.** |
| 3 | `a3143eed` | `feat(transport-netty)`: Netty 4.1 pipeline (length-frame → CBOR → idle → heartbeat → backpressure → inbound), `NettyClientTransport`, `NettyServerTransport`, virtual-thread business executor. **7 IT.** |
| 4 | `5b334642` | `feat(server-bus-v2)` foundation: `BusEvent` sealed + `BusEventBus`, `Connection`+`ConnectionRegistry` (supersede + token-guarded unregister), `ClusterRegistry` (RANDOM/FIRST pick), `CorrelationStore` (bounded shutdown + scheduler cleanup). **30 unit tests.** |
| 5 | `932dff1f` | `feat(server-bus-v2)` routing: `BundleRegistrationInfo`, `HandshakeHandler`, `BundleSession`, `ForwardingTable`, `MessageRouter` (dispatcher pattern), `BusLifecycle` orchestrator. **7 in-memory IT.** |
| 6 | `e31178ea` | `feat(server-bus-v2)` Spring wiring + bug fixes: `BusV2Properties`, `BusV2Configuration` (`@ConditionalOnProperty`), `BusV2Starter`. Handshake race fix (bind address before sending Welcome). `Response.error` → `Response.failure` rename (Jackson accessor scan conflict). Real-TCP `BusLifecycleNettyIT` (3 tests). |
| 7 | `3e46008a` | `feat(server-bus-v2)` disconnect handling: `ForwardingTable.drainInvolving` returns entries; `onTransportDisconnected` surfaces `Response.failure("peer disconnected: …")` to originators of in-flight forwarded requests. **`BusLifecycleDisconnectIT` adds 8 scenarios.** |
| 8 | `e74f2ec1` | `docs`: brought Claude operating context (`CLAUDE.md`, `.claude/PLAN.md`, `.claude/STATUS.md`) into the repo for portability. |
| 9 | `cc284865` | `feat(bundle-v2)`: full resilient bundle client + security + exactly-once. See dedicated section below. |
| 10 | `9ef5053c` | `docs(STATUS)`: updated for the bundle slice. |
| 11 | `4c887dca` | `feat(broker-rtt)`: zero-copy forwarding. `Frame` (Message + raw bytes), `Transport.onFrame` + `Transport.sendRaw`, `CborMessageDecoder` retains raw bytes, broker uses `sendRaw` on forwarded Request/Response → no CBOR re-encode on the broker hop. **`BusLifecycleZeroCopyIT` (2 tests)** asserts `forwardedRawCount == 2×N` and `forwardedReencodedCount == 0`. |
| 14 | _(this slice)_ | `feat(bundle-v2)`: PR3.2b + PR3.2c — `EventoBundle.Builder` migrated to compose on `BundleClient`; v1 transport classes deleted. New `EventoServerV2Adapter` implements `EventoServer` over `BundleClient` (gateways unchanged). New `BundleInboundDispatcher` ports the v1 switch-on-body-type lambda into the v2 `RequestHandler` byte-array contract, registered against every command / query payloadType. Server-side: new `BusEvent.AdminNotification` emitted from `BusLifecycle.onNotification`'s default branch + new `BundleAdminNotificationListener` Spring bean (active when `evento.server.bus.v2.enabled=true`) that handles the bundle-admin notification stream (performance metrics + consumer registration). New `ProtocolPayloadTypes.BUNDLE_ADMIN_NOTIFICATION` and `AdminPayloadCodec.encode/decodeMessage`. Deleted 1,634 LOC of v1 transport: `EventoServerClient` (448), `EventoSocketConnection` (465), `ClusterConnection` (256), `EventoSocketConfig`, `MessageHandler`, `ResponseSender`, `RequestHandler`, `EventoResponseSender`. Slimmed `EventoServerMessageBusConfiguration` to just the address list. Updated 6 demo configs. **+1 test** in new `AdminNotificationFlowIT` (bundle → server admin notification round-trip). |
| 13 | `ef6ede8e` | `feat(bundle-v2)`: PR3.2a — bundle-side admin request handler. Moved `AdminPayloadCodec` from server to `com.evento.common.admin` so bundles can decode the same wire. New `BundleAdminRequestHandler` in `evento-bundle/.../client/v2/admin/` — implements `HandlerRegistry.RequestHandler`, decodes the inner `EventoRequest`, dispatches one of the four `ConsumerXyzRequestMessage` operations via a `ConsumerLookup` SPI, encodes the `EventoResponse`. Closes the round-trip the dashboard / discovery / consumer endpoints rely on: `ConsumerService.getConsumerStatusFromNodes`, `setRetryForConsumerEvent`, `consumeDeadQueue`, `deleteDeadEventFromEventConsumer` now work end-to-end on the v2 wire. **+5 tests** in new `BundleAdminRoundTripIT`. |
| 12 | `4dacefd1` | `feat(server-bus)`: PR3.1 + autoscale rip-out. Two changes that land together because the autoscale rip-out reduced the surface PR3.1 was migrating. **PR3.1:** BusFacade SPI; v1 `MessageBusFacade` adapter + v2 `BusLifecycleFacade` adapter. Migrated `DashboardController`, `ClusterStatusController`, `AutoDiscoveryService`, `ConsumerService`, `ConsumerController` to depend on `BusFacade`. Enriched `BundleRegistrationInfo` with rich `RegisteredHandler` list + `payloadInfo` so auto-discovery still works on v2. New `BusEvent.BundleRegistered` event fired by `BusLifecycle.onNotification`. New `BusLifecycle.forward(NodeAddress, payloadType, byte[], Duration)` server-initiated RPC primitive. New `evento:server-admin-request` payloadType + `AdminPayloadCodec` (Jackson-CBOR with polymorphic typing, mirrors v1 `ObjectMapperUtils`) so v1's `EventoRequest` round-trips through the v2 wire. **Autoscale rip-out:** deleted `AutoscalingProtocol`, `ThreadCountAutoscalingProtocol`, `ClusterNodeIsBoredMessage`, `ClusterNodeIsSufferingMessage`, `ClusterNodeKillMessage`, `BundleDeployService`. Dropped `Bundle.{min,max}Instances` columns from schema + DTO + parser. Removed `sendKill` from `BusFacade`/`BusLifecycle` (+ `NOTIFY_KILL` from `ProtocolNotifications`), `/spawn` + `/kill` REST endpoints, and the `TracingAgent.arrival/departure` calls + `AutoscalingProtocol` field. The cluster orchestrator (k8s/whatever) now owns spawn/kill — the framework only emits performance metrics. **+9 tests:** 5 in `BusLifecycleIT` (BundleRegistered emission, forward primitive, waitUntilAvailable) + 4 in new `BusLifecycleFacadeNettyIT`. |

**Test totals on JDK 25:** 220 (transport-api 41, transport-netty 8,
server v2 **74** — includes 80 MB large-payload round-trip IT, consumer-v2 unit 52, bundle v2 engines 7, **evento-lab 16** — 6 in-memory + 6 connectivity + **4 command RTT**,
**evento-lab-ms-it 22** — 3 consumer lifecycle + 2 saga/payment + 2 reconnect + 2 order lifecycle + 2 payment saga + 3 notification + 2 multi-context + 3 RTT/stress + **3 ms command RTT**).
Postgres + MySQL JDBC IT (evento-consumer-state-store-jdbc-v2 + evento-lab) add 50+ more when Docker is healthy
(`EVENTO_RUN_JDBC_IT=true`). Run with:

```
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-transport-api:test :evento-transport-netty:test \
            :evento-server:test --tests 'com.evento.server.bus.v2.*' \
            :evento-common:test \
            :evento-bundle:test \
            :evento-consumer-state-store:evento-consumer-state-store-jdbc-v2:test \
            :evento-lab:test \
            :evento-lab-microservices:evento-lab-ms-it:test
```

To run the JDBC ITs against ephemeral containers:

```
EVENTO_RUN_JDBC_IT=true JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-consumer-state-store:evento-consumer-state-store-jdbc-v2:test
```

## Commit 9: bundle client + security + exactly-once (cc284865)

Closes the loop: there's now a working v2 client (`BundleClient`)
that talks to the v2 broker over an authenticated, optionally
TLS-encrypted, exactly-once-effective channel.

### Bundle client (`evento-bundle/src/main/java/com/evento/application/client/v2/`)

| File | Role |
|---|---|
| `BundleClient` | Public façade. Builder, `start/stop`, byte-oriented `request(...)`, `notify(...)`, `registerRequestHandler/registerNotificationHandler`, `enable/disable`. |
| `BundleClientConfig` (record + Builder) | host/port, identity, authToken, handler list, capabilities, timeouts, transport config, autoEnable. |
| `BundleClientState` (enum) | INITIAL → CONNECTING → HANDSHAKING → REGISTERING → READY → RECONNECTING → CLOSING → CLOSED. |
| `connection/ConnectionSupervisor` | Owns the Netty transport lifecycle and reconnect loop. `start()` future completes when bundle first reaches READY (the run loop blocks on session-end afterwards). |
| `correlation/BundleCorrelationTracker` | Outbound `UUID → CompletableFuture<Response>`, scheduler-driven expiry, bounded shutdown. |
| `dedup/ProcessedRequestCache` | Inbound dedup — `resolveOrClaim` returns Claimed / InFlight / Replay. LRU + TTL bounded. |
| `handler/HandlerRegistry` | `payloadType → RequestHandler / NotificationHandler`. Pure byte arrays at the framework boundary. |
| `handshake/HelloFactory` | Builds the `Hello` from config. |
| `InboundDispatcher` | Pattern-matches inbound: Response → tracker; Request → dedup + handler + reply; Notification → handler. Handlers run on the business executor (virtual threads). |

### Security

- `Hello.authToken: String` (new wire field; v2 pre-release so no
  migration needed).
- `Reject.CODE_AUTH_FAILED` for token rejection.
- `evento-server/.../bus/v2/security/TokenValidator` SPI with built-in
  `acceptAll()` (default) and `sharedSecret(token)` (constant-time
  compare). `BusLifecycle` calls it in `validateHello` and rejects
  with the reason string surfaced through the bundle's `start()`
  future.
- TLS via `NettyTransportConfig.sslContext` (nullable). When set, the
  pipeline prepends `SslHandler`. Same record for client and server
  (use `SslContextBuilder.forClient()` vs `forServer()`).

### Exactly-once QoS

Both halves of the contract are in:

- **Broker**: `correlation/ForwardingDedupCache` — LRU keyed by
  `correlationId`, TTL 5 min, 50 000 entries default. In
  `BusLifecycle.onRequest`: duplicate of an already-answered request
  → replay cached `Response` immediately, no forward. Duplicate of an
  in-flight request → silent drop. `onResponse` records the answer.
- **Bundle (handler side)**: `ProcessedRequestCache` already in
  `InboundDispatcher` — duplicate request replays cached `Response`,
  handler runs at most once per `correlationId`.

A caller bundle can retry with the same `correlationId` and get
exactly one side-effect plus the same response back.

### Protocol module

Wire-level constants moved into
`com.evento.transport.protocol.*` under `evento-transport-api`:
- `BundleRegistrationInfo` (was in `evento-server/.../handshake/`)
- `ProtocolNotifications` (`evento:bundle-registration`,
  `evento:enable`, `evento:disable`, `evento:kill`)

`BusLifecycle.NOTIFY_*` constants kept as forwarding aliases — no
caller has to churn.

### Tests (`BundleClientIT`, 8 scenarios)

Real-TCP `NettyServerTransport` + `BundleClient`:

1. **`requestRoutedToHandlerBundleAndResponseFlowsBack`** — happy
   path, exactly one handler invocation, payload echoed.
2. **`handlerExceptionPropagatesAsResponseError`** — handler throws
   → caller receives `Response.failure`.
3. **`wrongTokenIsRejectedAtHandshake`** — bad token → start()
   future fails with `AUTH_FAILED`.
4. **`missingTokenWhenServerRequiresOneIsRejected`** — null token
   → same.
5. **`duplicateInboundRequestReplaysCachedResponseWithoutReinvokingHandler`** —
   same `correlationId` twice → handler runs once, both replies match.
6. **`supervisorReconnectsAndRegistersHandlersAfterTransportDrop`**
   — yank transport, supervisor reopens + re-registers transparently.
7. **`encryptedRoundTripWithSelfSignedTls`** — same happy path over
   TLS. Skipped on JDKs that block Netty's `SelfSignedCertificate`
   (works on JDK ≤ 24; the TLS pipeline itself is exercised any time
   the cert can be minted).
8. **`requestBeforeStartFailsFast`** — `request()` while CONNECTING
   surfaces `SendFailedException` immediately.

### Zero-copy forwarding — landed in commit 11 (4c887dca)

The broker no longer re-encodes when relaying. Hot-path saving: one
CBOR encode + one byte[] allocation per relayed message. The
`forwardedRawCount` / `forwardedReencodedCount` counters on
`BusLifecycle` expose the ratio for observability. Tests pin the
contract: every Netty-to-Netty forward must use the raw path.

## Key design decisions (load-bearing, don't undo without thinking)

1. **Wire payload is opaque to the server.** `Request` / `Response` /
   `Notification` carry `payloadType: String` + `payload: byte[]`. The
   server routes by string; payloads are CBOR-encoded user types
   deserialized by the bundle via its own `PayloadCodec`. Server stays
   payload-agnostic — no business class loading on the server side.
2. **Sealed `Message` hierarchy with `@JsonTypeInfo`** + closed
   `MessageTypeRegistry` whitelist. Adding a wire type = extend permits +
   register a tag; the compiler enforces exhaustive dispatch.
3. **Two correlation maps, two purposes:**
   - `CorrelationStore` — server-initiated requests with `CompletableFuture`
     to await.
   - `ForwardingTable` — bundle-A → server → bundle-B relays; no future,
     just remembers where to send the response.
4. **One event stream, sealed `BusEvent`.** Replaces v1's four parallel
   listener lists (`viewListeners`, `availableViewListeners`,
   `joinListeners`, `leaveListeners`). Subscribers pattern-match.
5. **Lifecycle is explicit.** `BusLifecycle.start(port)` / `stop(Duration
   deadline)`. The Spring layer in `BusV2Starter` owns the
   `@PostConstruct`/`@PreDestroy` glue, so `BusLifecycle` stays plain
   Java. Constructors do no work.
6. **Bind address before sending Welcome.** A subtle ordering choice in
   `BusLifecycle.onHello` — without it, fast clients send follow-up
   notifications that arrive at the server before the session's
   `NodeAddress` is bound, and they get dropped as "before-handshake".

## Command RTT integration tests + CommandBrokerHandler fix

### Root cause

The v2 rewrite deleted `MessageBus.java` (PR3.5) but did not port its broker-side command interception logic. As a result, bundles received plain `DomainCommandMessage` instead of `DecoratedDomainCommandMessage` (aggregate story attached), events were never written to the event store, and projectors had nothing to consume.

### Fix

New Spring `@Component` **`CommandBrokerHandler`** (`evento-server/src/main/java/com/evento/server/es/`) restores all v1 broker logic:
- Subscribes to `BusEvent.BundleRegistered` and registers `BusLifecycle.LocalRequestHandler` instances for every `AggregateCommandHandler` and service `CommandHandler` payload type.
- **Aggregate path:** acquire optional resource lock → `EventStore.fetchAggregateStory` → wrap in `DecoratedDomainCommandMessage` → forward to bundle → persist `DomainEventMessage` + optional snapshot → return event to caller.
- **Service path:** acquire optional resource lock → forward as-is → persist `ServiceEventMessage` if `objectClass != null` → return event to caller.

### `BrokerEventStore` interface

`CommandBrokerHandler` was refactored to depend on a new `BrokerEventStore` interface (4 methods: `fetchAggregateStory`, `publishEvent`, `saveSnapshot`, `deleteAggregate`) instead of the concrete Spring `EventStore` class. `EventStore` implements it. This enables in-memory test implementations without a database.

### `PgDistributedLock` null-DataSource mode

When `DataSource` is null, `PgDistributedLock.acquire/tryAcquire/release` now skip the PostgreSQL advisory lock and fall back to the JVM-only semaphore. This is safe for embedded/single-JVM scenarios (like integration tests) where cross-process concurrency is not needed.

### Command RTT integration tests

**`evento-lab/src/test/java/com/evento/lab/CommandRttIT`** — 4 tests:
- `createOrder_aggregateStoresEventAndProjectorWritesView`: full RTT through Aggregate → EventStore → Projector → Projection
- `confirmOrder_serviceCommandWithLock_projectorUpdatesStatus`: service command with `lockId` (exercises JVM-only lock path)
- `cancelOrder_serviceCommandWithLock_projectorMarksAsCancelled`
- `listOrders_queryReturnsAllProjectedOrders`

**`evento-lab-microservices/evento-lab-ms-it/src/test/java/com/evento/lab/ms/it/MsCommandRttIT`** — 3 tests:
- `createOrder_commandToEventToProjection`: cross-bundle RTT (command bundle → broker → query bundle)
- `addItemsToOrder_aggregateReplayAndProjection`: verifies aggregate event replay for subsequent commands
- `listOrders_multipleOrdersAllProjected`

### New test support classes

| Class | Module | Purpose |
|---|---|---|
| `CommandAwareTestEventStore` | evento-lab test | Implements `BrokerEventStore` + BundleClient for `EventFetchRequest`; shared in-memory store |
| `CommandAwareEmbeddedBroker` | evento-lab test | Broker + `CommandBrokerHandler` + `TestGatewayClient` factory |
| `MsCommandAwareTestEventStore` | evento-lab-ms-it test | Ms equivalent of above |
| `MsCommandAwareEmbeddedBroker` | evento-lab-ms-it test | Ms equivalent of above |

## RC1 status — version bumped to `2.0.0-rc1`, tagged `v2.0.0-rc1`

`build.gradle` bumped from `2.0.0-SNAPSHOT` → `2.0.0-rc1`. Clean build + all 224 tests green on JDK 25. Git tag `v2.0.0-rc1` created on `next`. Next: deploy to staging, soak 1–2 weeks, then merge `next` → `main` and tag `v2.0.0`.

## PR3 status — ALL SLICES DONE

All five PR3 slices (3.1 → 3.5) are committed on `next`. The v2 rewrite
is feature-complete. Next step: staging soak + RC tag.

### 3.1 Consumer migration on the server + autoscale rip-out — DONE (commit 12)

The framework no longer manages cluster scaling. The `ClusterNodeIs{Bored,Suffering}Message` signals and the `BundleDeployService` spawn-script runner are gone; what remains in the performance layer is *just metrics* (`PerformanceInvocationsMessage` + `PerformanceServiceTimeMessage`, persisted by `PerformanceStoreService`). The external orchestrator (k8s / nomad / whatever) reads those metrics and owns spawn + kill.



All five touch points enumerated in `PLAN.md` now depend on the
`BusFacade` SPI in `com.evento.server.bus`. Both adapters are wired
in `BusFacadeConfiguration`:

- v1 path (default): `MessageBusFacade` wraps the legacy `MessageBus`
  and translates its four listener types into a single
  `Consumer<BusEvent>` stream — `BundleRegistration` → enriched
  `BusEvent.BundleRegistered(NodeAddress, BundleRegistrationInfo, ...)`
  so AutoDiscoveryService consumes the same shape on both paths.
- v2 path (`evento.server.bus.v2.enabled=true`):
  `BusLifecycleFacade` wraps `BusLifecycle` and CBOR-encodes outgoing
  `EventoRequest` under the new `evento:server-admin-request`
  payloadType via `AdminPayloadCodec`. Bundle handler for that
  payloadType will be wired in 3.2 alongside the annotation scanner.

Key files added: `bus/BusFacade.java`,
`bus/BusFacadeConfiguration.java`, `bus/v1adapter/MessageBusFacade.java`,
`bus/v2/BusLifecycleFacade.java`, `bus/v2/admin/AdminPayloadCodec.java`,
`transport-api/protocol/ProtocolPayloadTypes.java`.

### 3.2 Bundle migration (partial — see commit 13)

The v2 `BundleClient` exists under `evento-bundle/.../client/v2/` and
is fully tested. What's left here is *wiring* it to the existing
annotation-driven framework (`@CommandHandler` / `@EventHandler` /
`@QueryHandler` / `@Saga` / `@Aggregate`):

- **3.2a — DONE (commit 13):** v2 admin handler. `BundleAdminRequestHandler`
  closes the `BusFacade.forward` round-trip end-to-end.
- **3.2b — DONE (commit 14):** `EventoBundle.Builder.start()` composes on
  `BundleClient`. `EventoServerV2Adapter` implements `EventoServer` over
  `BundleClient` so `CommandGatewayImpl` / `QueryGatewayImpl` are unchanged.
  `BundleInboundDispatcher` ports the v1 switch-on-body-type lambda into
  the v2 byte-array `RequestHandler` shape; registered against every
  command / query payloadType the scanner declared. Wire admin handler
  from 3.2a for `SERVER_ADMIN_REQUEST`. New server-side
  `BusEvent.AdminNotification` + `BundleAdminNotificationListener` handle
  the bundle-admin notification stream (performance metrics + consumer
  registration), gated on `evento.server.bus.v2.enabled=true`.
- **3.2c — DONE (commit 14):** Deleted the v1 bundle-side transport.
  `EventoServerClient`, `EventoSocketConnection`, `ClusterConnection`,
  `EventoSocketConfig`, `MessageHandler`, `RequestHandler`,
  `ResponseSender`, `EventoResponseSender` — 1,634 LOC gone. Slimmed
  `EventoServerMessageBusConfiguration` to address-list only. The v1
  `MessageBus.java` (1,099 LOC) on the server side stays for now — it's
  still the active path when `evento.server.bus.v2.enabled` is unset
  (default). Server-side v1 deletion is part of 3.5.
- Delete the old transport classes:
  - `evento-bundle/.../bus/EventoSocketConnection.java` (465 lines)
  - `evento-bundle/.../bus/EventoServerClient.java` (452 lines)
  - `evento-bundle/.../bus/ClusterConnection.java` — failover now lives
    behind a strategy interface.
  - `evento-bundle/.../bus/EventoSocketConfig.java`

### 3.3 ConsumerStateStore SPI rewrite — DONE (this slice)

Replaces the v1 monolithic abstract class with a SOLID-clean split:

- **`ConsumerProcessor`** (concrete class in `consumer.v2.*`) owns the
  v1-shape consume loop methods (`consumeEventsForProjector/Observer/Saga`
  + their dead-event variants + `handleLastError` + `toConsumerStatus` +
  `getLastEventSequenceNumberSagaOrHead`). Holds no state — all
  correctness comes from the lock + optimistic version on the checkpoint.
- **Five focused SPIs:**
  - `ConsumerLock` — cross-instance exclusive zone per `consumerId`
    (PG `pg_try_advisory_lock(hashtext(id))` / MySQL `GET_LOCK(id, 0)`).
    `LockHandle` is `AutoCloseable`; the JDBC impl pins a session-scoped
    `Connection` for the handle's lifetime.
  - `ConsumerStateStore` — checkpoint (read / commit-with-optimistic-version
    / delete / listConsumers) + enabled flag + error history.
  - `SagaStateStore` — instance lookup by association + insert / update / delete.
    JDBC impl persists `state` as JSON(B) and a separate flat `associations`
    JSON(B) column for fast `->> ?` (PG) / `JSON_EXTRACT` (MySQL) lookups.
  - `DeadEventQueue` — per-consumer DLQ with retry flag, upsert-on-add.
  - `DedupeStore` — observer dedupe with sweep windows.
- **Schema** (single V1 migration per dialect, side-by-side with v1 tables):
  `evento_v2_consumer_state` + `evento_v2_saga_state` + `evento_v2_dead_event`
  + `evento_v2_dedupe`.
- **v1 `ConsumerStateStore` abstract class is untouched** and remains the
  active path. Engines bind to v2 in 3.4; v1 deletion is 3.5.

Note: one minor v1 change — `SagaState.getAssociations()` accessor added
(additive — v1 callers untouched) so v2 saga stores can persist the flat
association map without reflecting.

### 3.4 Saga / Projector / Observer engines — DONE (this slice)

New v2 engine classes under `evento-bundle/.../consumer/v2/` compose on
`ConsumerProcessor` + the v2 SPI bundle (`ConsumerStateStore`,
`DeadEventQueue`). `EngineSupervisor` owns the virtual-thread executor
and the deadlined shutdown. `ConsumerHandle` is the new shared admin
surface that lets the existing `BundleAdminRequestHandler.ConsumerLookup`
route to v1 or v2 consumers uniformly during the migration window.
v2 path is opt-in via `EventoBundle.Builder.consumerEngineConfigBuilder`;
v1 remains the default.

PLAN.md called for `StructuredTaskScope.ShutdownOnFailure`. On JDK 25
the stable scope API is owner-thread-scoped (open + close on the same
thread, close auto-joins). That's a great fit for short-lived parallel
fan-out, not for long-running engines that need to be started in one
method and stopped in another. We deliver the load-bearing property —
*no orphan threads, bounded stop deadline* — with a virtual-thread
executor; engines that want bounded fan-out within a single batch can
open their own scope inside `run()`.

### 3.5 Delete v1 — DONE (this slice)

All v1 code removed. `BusFacadeConfiguration` is unconditional. `BusV2Configuration` no longer conditional. v2 is the only runtime path.

**Next:** Tag `v2.0.0-rc1`, publish to Maven Central, soak 1–2 weeks with early adopters, then `v2.0.0`.

## Where the v2 code lives (cheat sheet)

```
evento-transport-api/src/main/java/com/evento/transport/
  ├── Transport.java                       Transport SPI (onMessage/onFrame, send/sendRaw)
  ├── TransportServer.java                 Server-side SPI
  ├── Frame.java                           parsed Message + raw wire bytes (zero-copy)
  ├── MessageDispatcher.java               OCP message dispatcher
  ├── HandshakeProtocol.java               PROTOCOL_VERSION + constants
  ├── codec/
  │   ├── Codec.java + JacksonCborCodec.java
  │   ├── PayloadCodec.java + JacksonCborPayloadCodec.java
  │   └── MessageTypeRegistry.java         whitelist
  ├── message/                             sealed Message hierarchy (+authToken on Hello, +AUTH_FAILED on Reject)
  ├── protocol/                            wire-level constants
  │   ├── ProtocolNotifications.java       evento:enable / disable / bundle-registration / bundle-discovery
  │   ├── ProtocolPayloadTypes.java        SERVER_ADMIN_REQUEST = evento:server-admin-request
  │   ├── BundleRegistrationInfo.java      lean only: bundleVersion + handlerPayloadTypes
  │   └── BundleDiscoveryInfo.java         rich: handlers + payloadInfo (sent post-enable)
  ├── reconnect/                           ReconnectStrategy + impl
  ├── state/                               ConnectionState + state machine
  └── inmemory/InMemoryTransport.java      test double

evento-transport-netty/src/main/java/com/evento/transport/netty/
  ├── NettyClientTransport.java            onFrame + sendRaw (zero-copy)
  ├── NettyServerTransport.java            child transports also onFrame + sendRaw
  ├── NettyTransportConfig.java            (optional SslContext)
  ├── EventoPipelineFactory.java           (prepends SslHandler when ssl set)
  ├── ChunkingEncoder.java                 outbound: FULL/CHUNK frame wrapping (transparent, any payload size)
  ├── ChunkReassembler.java                inbound: chunk reassembly by stream UUID
  ├── CborMessageDecoder.java              produces Frame(message, raw bytes)
  ├── CborMessageEncoder.java              encodes Message; passes ByteBuf through (for sendRaw)
  ├── HeartbeatHandler.java                IdleStateHandler bridge (Frame-aware)
  ├── BackpressureHandler.java
  └── MessageInboundHandler.java           Frame-typed VT-executor dispatch

evento-server/src/main/java/com/evento/server/bus/
  ├── BusFacade.java                       SPI used by Dashboard / ClusterStatus / Consumer / AutoDiscovery (PR3.1)
  ├── BusFacadeConfiguration.java          Spring wiring: v2 adapter always active (PR3.5)
  ├── NodeAddress.java                     shared
  └── v2/
      ├── BusLifecycleFacade.java          v2 → BusFacade adapter, EventoRequest ↔ byte[] codec (PR3.1)
      ├── admin/AdminPayloadCodec.java     CBOR + polymorphic typing for evento:server-admin-request (PR3.1)
      ├── event/                           BusEvent sealed (BundleRegistered lean + BundleDiscovered rich) + BusEventBus
      ├── registry/                        Connection/ConnectionRegistry/ClusterRegistry
      ├── correlation/                     CorrelationStore + ForwardingDedupCache (exactly-once)
      ├── handshake/                       HandshakeHandler
      ├── router/                          BundleSession + MessageRouter + ForwardingTable
      ├── security/                        TokenValidator SPI (acceptAll / sharedSecret built-ins)
      ├── lifecycle/BusLifecycle.java      orchestrator (+ forward / sendKill / waitUntilAvailable since PR3.1)
      └── spring/                          @Configuration + properties

evento-bundle/src/main/java/com/evento/application/consumer/
  ├── ConsumerHandle.java                  shared admin surface (PR3.4) — implemented by v2 engines
  └── v2/
      ├── ProjectorEngine.java             v2 engine, composes ConsumerProcessor + DLQ + state-store (PR3.4)
      ├── SagaEngine.java                  v2 saga engine (PR3.4)
      ├── ObserverEngine.java              v2 observer engine (PR3.4)
      ├── EngineSupervisor.java            virtual-thread executor + deadlined shutdown + findConsumer (PR3.4)
      └── ConsumerEngineConfig.java        builder-side bundle of v2 SPIs the engines need (PR3.4)

evento-bundle/src/main/java/com/evento/application/client/v2/
  ├── BundleClient.java                    public facade (Builder, request/notify/handlers)
  ├── BundleClientConfig.java              record + Builder
  ├── BundleClientState.java               INITIAL → … → CLOSED
  ├── InboundDispatcher.java               Response/Request/Notification dispatch
  ├── connection/ConnectionSupervisor.java Netty transport + reconnect supervisor
  ├── correlation/BundleCorrelationTracker.java
  ├── dedup/ProcessedRequestCache.java     bundle-side exactly-once (Claimed/InFlight/Replay)
  ├── handler/HandlerRegistry.java         payloadType → handler
  └── handshake/HelloFactory.java          builds Hello with token

evento-common/src/main/java/com/evento/common/messaging/consumer/v2/
  ├── ConsumerProcessor.java               concrete v1-shape consume loop (PR3.3)
  ├── ConsumerCheckpoint.java              sealed
  ├── EventCheckpoint.java                 record (observer)
  ├── SagaCheckpoint.java                  record
  ├── ProjectorCheckpoint.java             record
  ├── ConsumerStateStore.java              SPI: checkpoint + enable + error
  ├── ConsumerLock.java                    SPI: cross-instance exclusive zone
  ├── SagaStateStore.java                  SPI: saga instance lookup by association
  ├── DeadEventQueue.java                  SPI: per-consumer DLQ
  ├── DedupeStore.java                     SPI: observer dedupe
  ├── ConsumerErrorState.java              record
  ├── OptimisticLockException.java
  ├── VersionedCheckpoint.java
  └── impl/
      ├── InMemoryConsumerStateStore.java
      ├── InMemoryConsumerLock.java
      ├── InMemorySagaStateStore.java
      ├── InMemoryDeadEventQueue.java
      └── InMemoryDedupeStore.java

evento-consumer-state-store/evento-consumer-state-store-jdbc-v2/
  ├── src/main/java/com/evento/consumer/state/store/jdbc/v2/
  │   ├── SqlDialect.java                  POSTGRES / MYSQL — migration loc + upsert + JSON binds + saga lookup SQL
  │   ├── JdbcConsumerStateStore.java      checkpoint + enable + error history
  │   ├── JdbcConsumerLock.java            pg_try_advisory_lock / GET_LOCK; pins Connection per handle
  │   ├── JdbcSagaStateStore.java          JSON(B) state + flat associations column
  │   ├── JdbcDeadEventQueue.java          upsert-on-add, retry flag, getAll for dashboard
  │   ├── JdbcDedupeStore.java
  │   └── FlywayMigrator.java
  └── src/main/resources/db/migration/{postgres,mysql}/v2/V1__init_v2_consumer_state.sql
```

The v1 `MessageBus.java` has been **deleted** (PR3.5). The v2
`BusLifecycle` is now the only bus — `BusFacadeConfiguration` is
unconditional and `BusV2Configuration` no longer needs
`@ConditionalOnProperty`.

## Critical bug fixed: CommandBrokerHandler (post-RC1)

`CommandBrokerHandler` added to `evento-server/src/main/java/com/evento/server/es/`.

**What was missing:** The v1 `MessageBus` (deleted in PR3.5) contained the broker-side logic for
domain and service commands:
1. Acquire distributed lock on `lockId ?? aggregateId`
2. Fetch aggregate story (`fetchAggregateStory`) from `EventStore`
3. Wrap `DomainCommandMessage` in `DecoratedDomainCommandMessage` (with state + event stream)
4. Forward the decorated command to the aggregate bundle via `BusLifecycle.forward()`
5. Store the resulting `DomainEventMessage` in the event store (`publishEvent()`)
6. Optionally save the snapshot
7. Return the stored event to the caller (command gateway expects `EventMessage` back)

For service commands: same flow but no decoration + stores `ServiceEventMessage`.

**Symptom:** Event store was empty, projectors consumed nothing, bundles received plain
`DomainCommandMessage` where `BundleInboundDispatcher` expects `DecoratedDomainCommandMessage`
→ `IllegalArgumentException: unsupported inbound request body type`.

**Fix:** `CommandBrokerHandler` subscribes to `BusEvent.BundleDiscovered` (was `BundleRegistered` — updated as part of the two-phase registration split). For each registered
`AggregateCommandHandler` or service `CommandHandler`, it registers a `LocalRequestHandler` in
`BusLifecycle` that performs the full interception flow. Registration is idempotent (overwrite on
bundle restart).

## Staging bug fixes (post-RC1)

Two bugs found during staging soak and fixed (uncommitted, `next` branch):

### Fix A: "no handler for …" after reconnection — `BusLifecycle.onTransportDisconnected`

**Root cause:** When a bundle reconnects with the same `NodeAddress` (same `bundleId` + `instanceId`), the new `Hello` handler calls `ConnectionRegistry.register(newConn)` which supersedes the old connection and closes its transport. That close fires a `DISCONNECTED` state change, triggering `onTransportDisconnected(oldSession)`. The token mismatch in `connectionRegistry.unregister(…, oldToken, …)` correctly returns empty (no-op), but the immediately following `clusterRegistry.removeNode(address)` was unconditional — it wiped the handlers that the new connection had just submitted (or was about to submit).

**Fix:** `onTransportDisconnected` now returns early when `unregister` returns empty (token mismatch = superseded session). `clusterRegistry.removeNode`, `forwardingTable.drainInvolving`, and `correlationStore.failMatching` only run when we actually removed the connection. Added `event=disconnect_superseded_skip` log line for observability.

**Files changed:** `BusLifecycle.java`

### Fix B: SQL `23503` FK violation on `core__handler_return_type_name_fkey`

**Root cause:** The orphan-payload cleanup in `BundleService.register` and `BundleService.unregister` iterates all payloads and deletes those whose `registeredIn` bundle no longer exists. It did not check whether the payload was still referenced by handlers in *other* bundles (as `return_type_name`, `handled_payload_name`, or an invocation entry). Postgres's FK constraint correctly blocked the delete; the old code swallowed the exception with `catch (Exception ignored)`, causing noisy `SqlExceptionHelper` ERROR logs without a crash.

**Fix:** Added `HandlerRepository.existsByHandledPayload_Name`, `existsByReturnType_Name`, and `existsByInvocationPayloadName` (native query on `core__handler__invocation`). `HandlerService.isPayloadReferenced(name)` combines all three. Both orphan-cleanup loops now gate deletion on `!handlerService.isPayloadReferenced(payload.getName())` and remove the `try-catch`. The noisy errors are gone and stale payloads that are still referenced are left in place (correct behaviour — they're not truly orphaned).

**Files changed:** `HandlerRepository.java`, `HandlerService.java`, `BundleService.java`

## Staging instability fixes (post-RC1, uncommitted on `next`)

Two production bugs found during staging soak and fixed in the current session:

### Fix C: DEGRADED channel drops in-flight responses (primary production issue)

**Root cause:** `ConnectionState.canSend()` returned `true` only for CONNECTED. When a channel's outbound buffer crossed the high-water mark, Netty set the state to DEGRADED. `ServerChildTransport.send()` and `sendRaw()` both guarded on `canSend()` → threw `SendFailedException: not in CONNECTED state: DEGRADED`. In-flight responses from handler bundles were silently dropped; the originator bundle waited 30 seconds for its correlation timer to fire.

**Fix:** `ConnectionState.canSend()` now returns `true` for DEGRADED as well. DEGRADED is advisory backpressure — the TCP socket is alive, Netty will queue the write and flush it when the buffer drains. Responses are critical-path traffic and must never be dropped due to backpressure.

**Collateral changes:**
- `InMemoryTransport.simulateDisconnect()` corrected from DEGRADED → DISCONNECTED (was conflating two semantically different states).
- `ConnectionStateMachineTest.canSendOnlyWhenConnected` renamed and updated to assert DEGRADED is sendable.
- `RoundTripFlowTest.sendWhileDisconnectedFails` error-message assertion updated from "DEGRADED" to "DISCONNECTED".

**Files changed:** `ConnectionState.java`, `InMemoryTransport.java`, `ConnectionStateMachineTest.java`, `RoundTripFlowTest.java`

### Fix D: TCP disconnect + reconnect loses in-flight responses (QoS reconnect delivery)

**Root cause A (server-side):** `onTransportDisconnected()` called `forwardingTable.drainInvolving(address)` which removed *both* destination-side and originator-side forwarding entries. When the originator disconnected mid-flight, the handler's eventual reply found no forwarding entry and was treated as an orphan response.

**Root cause B (server-side):** When the handler replied after the originator disconnected, `connectionRegistry.lookup(originator)` returned empty and the response was dropped with `event=response_originator_gone`.

**Fix:**
1. `ForwardingTable.drainByDestination(NodeAddress)` — new method that drains only entries where the address is the *destination* (handler). Originator-side entries remain in the table.
2. `onTransportDisconnected()` now calls `drainByDestination()` instead of `drainInvolving()`. Handler-disconnect errors still surface immediately to the originator; originator-disconnect entries stay alive so the handler's eventual reply can be routed to the reconnected transport.
3. `onResponse()` — if `connectionRegistry.lookup(originator)` is empty (originator still disconnected), the response is buffered in `reconnectBuffer` (keyed by `instanceId`, 2-minute TTL) instead of being dropped.
4. `onHello()` — after a successful handshake, `deliverPendingResponses(address, transport)` drains any buffered responses for the reconnecting instance and forwards them to the new transport. The bundle's `BundleCorrelationTracker` still has the pending futures (it does not call `failAll()` on disconnect, only on shutdown), so the futures complete normally.

**New tests in `BusLifecycleDisconnectIT`:**
- Test 9: `inFlightRequestDeliveredAfterCallerReconnects` — handler replies after caller reconnects; ForwardingTable entry routes to new transport.
- Test 10: `inFlightResponseBufferedAndDeliveredOnCallerReconnect` — handler replies while caller is disconnected; server buffers response; replays on reconnect.

**Files changed:** `ConnectionState.java` (shared with Fix C), `ForwardingTable.java`, `BusLifecycle.java`, `BusLifecycleDisconnectIT.java`

## Resume checklist (next session)

RC1 tagged. Remaining work before `v2.0.0`:

1. ✅ `git checkout next`
2. ✅ Clean compile: `./gradlew clean build -x test`
3. ✅ Full test suite green (192 tests on JDK 25)
4. ✅ Version bumped to `2.0.0-rc1`, git tag `v2.0.0-rc1` created
5. ✅ `evento-lab` end-to-end integration module added (6 in-memory + 6 connectivity tests green)
6. ✅ `evento-lab` api-package refactor (`com.evento.lab.api.*`) + `evento-lab-microservices` multi-bundle RECQ example + 7 new IT tests (total 199 on JDK 25)
7. ✅ Post-RC1 staging fixes: routing-table race on reconnect + FK violation on payload delete
8. ✅ CBOR 20 MB string cap removed from `AdminPayloadCodec`; `SerializedQueryResponse` eliminated from v2 transport path; peak heap for 80 MB query response ~400 MB → ~240 MB.
9. ✅ Staging instability: DEGRADED channel no longer drops in-flight responses; TCP disconnect + reconnect now delivers buffered responses (Fixes C + D above, uncommitted).
10. Commit Fixes C + D.
11. Deploy `next` to staging with `evento-demo` + traffic generator.
12. Soak 1–2 weeks with early adopters.
13. Merge `next` → `main`, tag `v2.0.0`, push to Maven Central.

## How to run locally

- JDK: 25.0.x (download with SDKMAN or use `/usr/libexec/java_home`).
- `./gradlew projects` lists all modules including `evento-transport-api`,
  `evento-transport-netty`, `evento-consumer-state-store-jdbc-v2`.
- v2 bus is now the only bus — no `@ConditionalOnProperty` guard needed.

## Notes for Claude on resume

The plan in `PLAN.md` is authoritative for major decisions. This file
(`STATUS.md`) is a delta that should be updated at the end of each
session. The pattern for commits has been:

- `chore(build): …` for toolchain.
- `feat(transport-api): …` / `feat(transport-netty): …` for module work.
- `feat(server-bus-v2): …` for the server bus rewrite.

Each commit message has rich context — `git log --oneline next` plus
`git show <hash>` is the fastest way to recover context.
