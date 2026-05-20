# Evento v2.0 rewrite — status snapshot

Last updated at the end of the disconnect-IT slice. Snapshot lives in
the repo so the work can resume from another machine without losing
context. Companion docs in this folder:

- [`PLAN.md`](PLAN.md) — the approved, full v2.0 rewrite plan (3 PRs)
- `STATUS.md` (this file) — what's done, what's next

## Branch

All work is on **`next`**, branched off `main`. Do not push to `main`;
`next` will be promoted to `main` as `v2.0.0` only after PR3 ships and a
1–2 week staging soak.

## What's done (11 commits beyond `main`, ~9 000 lines of new code)

| # | Commit | What landed |
|---|---|---|
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
| 13 | _(this slice)_ | `feat(bundle-v2)`: PR3.2a — bundle-side admin request handler. Moved `AdminPayloadCodec` from server to `com.evento.common.admin` so bundles can decode the same wire. New `BundleAdminRequestHandler` in `evento-bundle/.../client/v2/admin/` — implements `HandlerRegistry.RequestHandler`, decodes the inner `EventoRequest`, dispatches one of the four `ConsumerXyzRequestMessage` operations via a `ConsumerLookup` SPI, encodes the `EventoResponse`. Closes the round-trip the dashboard / discovery / consumer endpoints rely on: `ConsumerService.getConsumerStatusFromNodes`, `setRetryForConsumerEvent`, `consumeDeadQueue`, `deleteDeadEventFromEventConsumer` now work end-to-end on the v2 wire. **+5 tests** in new `BundleAdminRoundTripIT`: each admin op routes correctly + unknown-consumer surfaces `ExceptionWrapper` on the facade callback. |
| 12 | `4dacefd1` | `feat(server-bus)`: PR3.1 + autoscale rip-out. Two changes that land together because the autoscale rip-out reduced the surface PR3.1 was migrating. **PR3.1:** BusFacade SPI; v1 `MessageBusFacade` adapter + v2 `BusLifecycleFacade` adapter. Migrated `DashboardController`, `ClusterStatusController`, `AutoDiscoveryService`, `ConsumerService`, `ConsumerController` to depend on `BusFacade`. Enriched `BundleRegistrationInfo` with rich `RegisteredHandler` list + `payloadInfo` so auto-discovery still works on v2. New `BusEvent.BundleRegistered` event fired by `BusLifecycle.onNotification`. New `BusLifecycle.forward(NodeAddress, payloadType, byte[], Duration)` server-initiated RPC primitive. New `evento:server-admin-request` payloadType + `AdminPayloadCodec` (Jackson-CBOR with polymorphic typing, mirrors v1 `ObjectMapperUtils`) so v1's `EventoRequest` round-trips through the v2 wire. **Autoscale rip-out:** deleted `AutoscalingProtocol`, `ThreadCountAutoscalingProtocol`, `ClusterNodeIsBoredMessage`, `ClusterNodeIsSufferingMessage`, `ClusterNodeKillMessage`, `BundleDeployService`. Dropped `Bundle.{min,max}Instances` columns from schema + DTO + parser. Removed `sendKill` from `BusFacade`/`BusLifecycle` (+ `NOTIFY_KILL` from `ProtocolNotifications`), `/spawn` + `/kill` REST endpoints, and the `TracingAgent.arrival/departure` calls + `AutoscalingProtocol` field. The cluster orchestrator (k8s/whatever) now owns spawn/kill — the framework only emits performance metrics. **+9 tests:** 5 in `BusLifecycleIT` (BundleRegistered emission, forward primitive, waitUntilAvailable) + 4 in new `BusLifecycleFacadeNettyIT`. |

**Test totals on JDK 25:** 120 (transport-api 45, transport-netty 7,
server v2 68). Run with:

```
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-transport-api:test :evento-transport-netty:test \
            :evento-server:test --tests 'com.evento.server.bus.v2.*'
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

## What's left for PR3 (next session)

The v2 server engine, bundle client, and the BusFacade abstraction
through the controllers are all in place. PR3 finishes the bundle
migration, the consumer state store, the saga/projector engines, then
deletes v1.

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
  decodes the `evento:server-admin-request` payload, runs the in-bundle
  dispatch (consumer status / set retry / process dead queue / delete
  dead event), replies with an `EventoResponse`. Closes the
  `BusFacade.forward` round-trip end-to-end.
- **3.2b — TODO:** Migrate `EventoBundle.Builder.start()` to compose on
  top of `BundleClient` instead of `EventoServerClient` /
  `EventoSocketConnection`. The existing manager scan (lines 442-575)
  stays — its output (the `ArrayList<RegisteredHandler>` + payload
  schema map) now feeds `BundleClientConfig.registeredHandlers` +
  `payloadInfo`. The v1 switch-on-body-type inbound dispatch becomes
  per-payloadType `BundleClient.registerRequestHandler` calls
  (`DecoratedDomainCommandMessage` → AggregateManager,
  `ServiceCommandMessage` → ServiceManager, `QueryMessage` →
  ProjectionManager). Wire `BundleAdminRequestHandler` from 3.2a for
  the admin path. Touch points: EventoBundle.java:585-650 and the
  gateways consuming `EventoServer` — easiest path is a thin
  `EventoServer`-implementing adapter over `BundleClient` so
  gateway code stays unchanged.
- **3.2c — TODO:** After 3.2b is stable, delete `EventoSocketConnection.java`
  (465 LOC), `EventoServerClient.java` (448 LOC), `ClusterConnection.java`
  (256 LOC), `EventoSocketConfig.java`. Drop the v1 `MessageBus.java`
  on the server side too.
- Delete the old transport classes:
  - `evento-bundle/.../bus/EventoSocketConnection.java` (465 lines)
  - `evento-bundle/.../bus/EventoServerClient.java` (452 lines)
  - `evento-bundle/.../bus/ClusterConnection.java` — failover now lives
    behind a strategy interface.
  - `evento-bundle/.../bus/EventoSocketConfig.java`

### 3.3 ConsumerStateStore SPI rewrite
`evento-consumer-state-store/**` — see `PLAN.md` Section 3.2 for the
new SPI (`ConsumerCheckpoint` sealed, `commit` with optimistic
versioning, `DedupeStore`).

### 3.4 Saga / Projector / Observer engines
Under `evento-bundle/.../consumer/v2/`, structured concurrency via JDK
25 `StructuredTaskScope`.

### 3.5 Delete v1
Once 3.1–3.4 land and pass IT:

- Delete `evento-server/.../bus/MessageBus.java` (1099 lines).
- Delete v1 wire-format types under
  `evento-common/.../modeling/messaging/message/internal/` that are no
  longer referenced.
- Tag `v2.0.0-rc1`, publish to Maven Central, soak 1–2 weeks with
  early adopters, then `v2.0.0`.

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
  │   ├── ProtocolNotifications.java       evento:enable / disable / kill / bundle-registration
  │   ├── ProtocolPayloadTypes.java        SERVER_ADMIN_REQUEST = evento:server-admin-request
  │   └── BundleRegistrationInfo.java      enriched with handlers + payloadInfo (PR3.1)
  ├── reconnect/                           ReconnectStrategy + impl
  ├── state/                               ConnectionState + state machine
  └── inmemory/InMemoryTransport.java      test double

evento-transport-netty/src/main/java/com/evento/transport/netty/
  ├── NettyClientTransport.java            onFrame + sendRaw (zero-copy)
  ├── NettyServerTransport.java            child transports also onFrame + sendRaw
  ├── NettyTransportConfig.java            (optional SslContext)
  ├── EventoPipelineFactory.java           (prepends SslHandler when ssl set)
  ├── CborMessageDecoder.java              produces Frame(message, raw bytes)
  ├── CborMessageEncoder.java              encodes Message; passes ByteBuf through (for sendRaw)
  ├── HeartbeatHandler.java                IdleStateHandler bridge (Frame-aware)
  ├── BackpressureHandler.java
  └── MessageInboundHandler.java           Frame-typed VT-executor dispatch

evento-server/src/main/java/com/evento/server/bus/
  ├── BusFacade.java                       SPI used by Dashboard / ClusterStatus / Consumer / AutoDiscovery (PR3.1)
  ├── BusFacadeConfiguration.java          Spring wiring: v1 adapter default, v2 adapter when bus.v2.enabled (PR3.1)
  ├── MessageBus.java                      v1 legacy (untouched)
  ├── NodeAddress.java                     shared
  ├── v1adapter/MessageBusFacade.java      v1 → BusFacade adapter, listener-to-BusEvent bridge (PR3.1)
  └── v2/
      ├── BusLifecycleFacade.java          v2 → BusFacade adapter, EventoRequest ↔ byte[] codec (PR3.1)
      ├── admin/AdminPayloadCodec.java     CBOR + polymorphic typing for evento:server-admin-request (PR3.1)
      ├── event/                           BusEvent sealed (incl. BundleRegistered, PR3.1) + BusEventBus
      ├── registry/                        Connection/ConnectionRegistry/ClusterRegistry
      ├── correlation/                     CorrelationStore + ForwardingDedupCache (exactly-once)
      ├── handshake/                       HandshakeHandler
      ├── router/                          BundleSession + MessageRouter + ForwardingTable
      ├── security/                        TokenValidator SPI (acceptAll / sharedSecret built-ins)
      ├── lifecycle/BusLifecycle.java      orchestrator (+ forward / sendKill / waitUntilAvailable since PR3.1)
      └── spring/                          @Configuration + properties

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
```

The v1 `MessageBus.java` is **untouched** under the same package as
`bus.v2` and continues to be the only Spring bean instantiated at
startup (the v2 `BusLifecycle` is gated behind
`evento.server.bus.v2.enabled=true` and not enabled in any
configuration yet).

## Resume checklist (next session)

1. `git checkout next`
2. `./gradlew clean build -x test` — confirm everything still compiles
   on the destination machine (Java 25 required).
3. Run the full v2 test suite (command above) — should be 96 / 96 green.
4. Pick the next slice from "What's left for PR3" — recommended order
   is 3.1 (consumer migration) first since it's small and additive;
   then 3.2 (bundle rewrite) which is the big one.

## How to run locally

- JDK: 25.0.x (download with SDKMAN or use `/usr/libexec/java_home`).
- `./gradlew projects` should list the new modules `evento-transport-api`
  and `evento-transport-netty`.
- The v1 server still boots and works exactly as before — v2 code is
  purely additive.

## Notes for Claude on resume

The plan in `PLAN.md` is authoritative for major decisions. This file
(`STATUS.md`) is a delta that should be updated at the end of each
session. The pattern for commits has been:

- `chore(build): …` for toolchain.
- `feat(transport-api): …` / `feat(transport-netty): …` for module work.
- `feat(server-bus-v2): …` for the server bus rewrite.

Each commit message has rich context — `git log --oneline next` plus
`git show <hash>` is the fastest way to recover context.
