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

## What's done (9 commits beyond `main`, ~8 500 lines of new code)

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

**Test totals on JDK 25:** 104 (transport-api 45, transport-netty 7,
server v2 52). Run with:

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

### Deferred to v2.1: zero-copy forwarding

The broker still does CBOR decode + re-encode when relaying a
`Request` between bundles. A real but bounded RTT win (a couple of µs
per RPC compared to network latency); needs a `Codec`/`Transport`
extension (raw `ByteBuf` write path). The wire format permits it;
this commit doesn't change anything that would block it.

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

The v2 server engine **and** bundle client are complete and tested.
PR3 wires the v2 stack into the existing Spring controllers and
deletes the v1 transport.

### 3.1 Consumer migration on the server
Touch points enumerated in `PLAN.md` Section "MessageBus consumers":

- `evento-server/.../web/DashboardController.java` — `messageBus.getCurrentAvailableView()` → `busLifecycle.availableView()`.
- `evento-server/.../web/ClusterStatusController.java` — `getCurrentView`, `getCurrentAvailableView`, `addViewListener`, `addAvailableViewListener`, `sendKill`. SSE callbacks become `busLifecycle.subscribe(Consumer<BusEvent>)` + `case BusEvent.ViewChanged v -> …`.
- `evento-server/.../service/discovery/AutoDiscoveryService.java` — `addJoinListener` / `addLeaveListener` → `subscribe(Consumer<BusEvent>)` + pattern match.
- `evento-server/.../service/discovery/ConsumerService.java` (4 forward call sites) — needs a `BusLifecycle.forward(NodeAddress to, Request, …)` method built atop `CorrelationStore`. Add to `BusLifecycle` API.
- `evento-server/.../web/ConsumerController.java` — passes `messageBus` to `ConsumerService` methods; update signature once `ConsumerService` is migrated.

### 3.2 Bundle migration (mostly done — see commit 9)
The v2 `BundleClient` exists under
`evento-bundle/.../client/v2/` and is fully tested. What's left here
is *wiring* it to the existing annotation-driven framework
(`@CommandHandler` / `@EventHandler` / `@QueryHandler` / `@Saga` /
`@Aggregate`):

- Build a scanner that maps annotated handler methods to
  `BundleClient.registerRequestHandler(payloadType, …)` calls.
- Migrate the v1 high-level `EventoBundle.Builder` to compose on top
  of `BundleClient` instead of `EventoSocketConnection`.
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
  ├── Transport.java                       Transport SPI
  ├── TransportServer.java                 Server-side SPI
  ├── MessageDispatcher.java               OCP message dispatcher
  ├── HandshakeProtocol.java               PROTOCOL_VERSION + constants
  ├── codec/
  │   ├── Codec.java + JacksonCborCodec.java
  │   ├── PayloadCodec.java + JacksonCborPayloadCodec.java
  │   └── MessageTypeRegistry.java         whitelist
  ├── message/                             sealed Message hierarchy (+authToken on Hello, +AUTH_FAILED on Reject)
  ├── protocol/                            wire-level constants
  │   ├── ProtocolNotifications.java       evento:enable / disable / kill / bundle-registration
  │   └── BundleRegistrationInfo.java
  ├── reconnect/                           ReconnectStrategy + impl
  ├── state/                               ConnectionState + state machine
  └── inmemory/InMemoryTransport.java      test double

evento-transport-netty/src/main/java/com/evento/transport/netty/
  ├── NettyClientTransport.java
  ├── NettyServerTransport.java
  ├── NettyTransportConfig.java            (now also holds optional SslContext)
  ├── EventoPipelineFactory.java           (prepends SslHandler when ssl set)
  ├── Cbor{Message,}{Encoder,Decoder}.java
  ├── HeartbeatHandler.java                IdleStateHandler bridge
  ├── BackpressureHandler.java
  └── MessageInboundHandler.java           VT-executor dispatch

evento-server/src/main/java/com/evento/server/bus/v2/
  ├── event/                               BusEvent sealed + BusEventBus
  ├── registry/                            Connection/ConnectionRegistry/ClusterRegistry
  ├── correlation/                         CorrelationStore + ForwardingDedupCache (exactly-once)
  ├── handshake/                           HandshakeHandler
  ├── router/                              BundleSession + MessageRouter + ForwardingTable
  ├── security/                            TokenValidator SPI (acceptAll / sharedSecret built-ins)
  ├── lifecycle/BusLifecycle.java          orchestrator (token auth + dedup integrated)
  └── spring/                              @Configuration + properties

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
