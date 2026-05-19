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

## What's done (7 commits beyond `main`, ~6 600 lines of new code)

| # | Commit | What landed |
|---|---|---|
| 1 | `a6f7eab3` | `chore(build)`: Java 21 → 25, Gradle 8.5 → 9.0, Spring Boot 3.2 → 3.5.5, Jackson 2.15 → 2.18.2 (+ CBOR), Lombok 1.18.40, JUnit 5.11.4, AssertJ. Centralized versions in `build.gradle` `allprojects { ext { … } }`. |
| 2 | `0038672c` | `feat(transport-api)`: new wire SPI module — sealed `Message` records (Hello/Welcome/Reject/Ping/Pong/Request/Response/Notification), `Codec`, `PayloadCodec`, `Transport`, `TransportServer`, `ConnectionStateMachine`, `ExponentialBackoffWithJitter`, `MessageDispatcher`, `MessageTypeRegistry`, `InMemoryTransport`. **45 tests.** |
| 3 | `a3143eed` | `feat(transport-netty)`: Netty 4.1 pipeline (length-frame → CBOR → idle → heartbeat → backpressure → inbound), `NettyClientTransport`, `NettyServerTransport`, virtual-thread business executor. **7 IT.** |
| 4 | `5b334642` | `feat(server-bus-v2)` foundation: `BusEvent` sealed + `BusEventBus`, `Connection`+`ConnectionRegistry` (supersede + token-guarded unregister), `ClusterRegistry` (RANDOM/FIRST pick), `CorrelationStore` (bounded shutdown + scheduler cleanup). **30 unit tests.** |
| 5 | `932dff1f` | `feat(server-bus-v2)` routing: `BundleRegistrationInfo`, `HandshakeHandler`, `BundleSession`, `ForwardingTable`, `MessageRouter` (dispatcher pattern), `BusLifecycle` orchestrator. **7 in-memory IT.** |
| 6 | `e31178ea` | `feat(server-bus-v2)` Spring wiring + bug fixes: `BusV2Properties`, `BusV2Configuration` (`@ConditionalOnProperty`), `BusV2Starter`. Handshake race fix (bind address before sending Welcome). `Response.error` → `Response.failure` rename (Jackson accessor scan conflict). Real-TCP `BusLifecycleNettyIT` (3 tests). |
| 7 | `3e46008a` | `feat(server-bus-v2)` disconnect handling: `ForwardingTable.drainInvolving` returns entries; `onTransportDisconnected` surfaces `Response.failure("peer disconnected: …")` to originators of in-flight forwarded requests. **`BusLifecycleDisconnectIT` adds 8 scenarios.** |

**Test totals on JDK 25:** 96 (transport-api 45, transport-netty 7,
server v2 44). Run with:

```
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-transport-api:test :evento-transport-netty:test \
            :evento-server:test --tests 'com.evento.server.bus.v2.*'
```

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

## What's left for PR3 (next session, ~5–6 weeks of work)

The v2 server engine is complete and tested. PR3 swaps over the rest
of the framework so v2 becomes the production stack.

### 3.1 Consumer migration on the server
Touch points enumerated in `PLAN.md` Section "MessageBus consumers":

- `evento-server/.../web/DashboardController.java` — `messageBus.getCurrentAvailableView()` → `busLifecycle.availableView()`.
- `evento-server/.../web/ClusterStatusController.java` — `getCurrentView`, `getCurrentAvailableView`, `addViewListener`, `addAvailableViewListener`, `sendKill`. SSE callbacks become `busLifecycle.subscribe(Consumer<BusEvent>)` + `case BusEvent.ViewChanged v -> …`.
- `evento-server/.../service/discovery/AutoDiscoveryService.java` — `addJoinListener` / `addLeaveListener` → `subscribe(Consumer<BusEvent>)` + pattern match.
- `evento-server/.../service/discovery/ConsumerService.java` (4 forward call sites) — needs a `BusLifecycle.forward(NodeAddress to, Request, …)` method built atop `CorrelationStore`. Add to `BusLifecycle` API.
- `evento-server/.../web/ConsumerController.java` — passes `messageBus` to `ConsumerService` methods; update signature once `ConsumerService` is migrated.

### 3.2 Bundle rewrite (the big one)
The bundle has to speak v2 wire. Files in scope:

- `evento-bundle/.../bus/EventoSocketConnection.java` (465 lines) — delete; replace with `EventoBundleClient` composed of `BundleTransport(NettyClientTransport)`, `ClusterFailoverStrategy`, `CorrelationTracker`, `RequestDispatcher`, `IncomingMessageDispatcher`, `ConsumerRegistry`, `HandshakeBootstrap`, `BundleLifecycle`.
- `evento-bundle/.../bus/EventoServerClient.java` (452 lines) — delete.
- `evento-bundle/.../bus/ClusterConnection.java` — delete (no more `System.exit(1)`; failover via injected strategy).
- `evento-bundle/.../bus/EventoSocketConfig.java` — delete; settings are part of `NettyTransportConfig` + a new bundle-side `BundleConfig`.

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
  ├── message/                             sealed Message hierarchy
  ├── reconnect/                           ReconnectStrategy + impl
  ├── state/                               ConnectionState + state machine
  └── inmemory/InMemoryTransport.java      test double

evento-transport-netty/src/main/java/com/evento/transport/netty/
  ├── NettyClientTransport.java
  ├── NettyServerTransport.java
  ├── NettyTransportConfig.java
  ├── EventoPipelineFactory.java
  ├── Cbor{Message,}{Encoder,Decoder}.java
  ├── HeartbeatHandler.java                IdleStateHandler bridge
  ├── BackpressureHandler.java
  └── MessageInboundHandler.java           VT-executor dispatch

evento-server/src/main/java/com/evento/server/bus/v2/
  ├── event/                               BusEvent sealed + BusEventBus
  ├── registry/                            Connection/ConnectionRegistry/ClusterRegistry
  ├── correlation/                         PendingCorrelation + CorrelationStore
  ├── handshake/                           BundleRegistrationInfo + HandshakeHandler
  ├── router/                              BundleSession + MessageRouter + ForwardingTable
  ├── lifecycle/BusLifecycle.java          orchestrator
  └── spring/                              @Configuration + properties
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
