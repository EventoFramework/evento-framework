# evento-lab

Single-bundle lab that demonstrates all RECQ concepts (aggregate, service, projector,
projection, saga, observer) wired into one `EventoBundle`. Use it as a reference for
how the framework components relate to each other and as a fast test target — no Docker
required for the core suite.

## Package layout

```
com.evento.lab.api/
  command/   CreateOrderCommand (DomainCommand), ConfirmOrderCommand, CancelOrderCommand (ServiceCommand)
  event/     OrderCreatedEvent (DomainEvent), OrderConfirmedEvent, OrderCancelledEvent (ServiceEvent)
  query/     FindOrderByIdQuery, ListOrdersQuery
  view/      OrderView

com.evento.lab.bundle/
  command/   LabAggregate — handles CreateOrderCommand, emits OrderCreatedEvent
             LabService   — handles Confirm/CancelOrderCommand, emits corresponding ServiceEvents
  consumer/  LabProjector — projects order state into LabStore
             LabSaga      — tracks saga lifecycle (created → confirmed / cancelled)
             LabObserver  — records seen events into LabStore.observedEvents
  query/     LabProjection — handles FindOrderByIdQuery, ListOrdersQuery
  LabStore   — thread-safe in-memory singleton used as assertion surface in tests
```

`api/` payload types are kept separate from bundle code so they can be shared across
modules without pulling in framework internals — the same convention used by `evento-demo-api`.

## Test infrastructure

| Class | Role |
|---|---|
| `EmbeddedBroker` | Starts a real v2 broker (`BusLifecycle` + `NettyServerTransport`) on a random port |
| `TestEventStoreBundleClient` | In-process event journal; handles `EventFetchRequest` + `EventLastSequenceNumberRequest` so consumer engines can replay events |

No mocks. The full connection stack (TCP transport → broker → bundle client → consumer engines)
exercises real code in every test.

> **Note on aggregate commands.** `DecoratedDomainCommandMessage` decoration (fetching the
> aggregate event stream before dispatching a command) requires a running event-store service
> RPC that is not implemented in `TestEventStoreBundleClient`. Tests exercise the consumer
> pipeline only — events are published directly via `TestEventStoreBundleClient.publish()`.

## Running the tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-lab:test
```

All tests run on JDK 25 without Docker. The JDBC-backed variants need Docker:

```bash
EVENTO_RUN_JDBC_IT=true JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-lab:test
```

## Test inventory

### `InMemoryConsumerIT` (3 tests)

Consumer pipeline correctness with in-memory state stores.

| Test | Objective |
|---|---|
| `projectorProcessesPublishedEvents` | Publish 5 events *before* the bundle starts; verify the projector replays all of them on startup (catch-up from position 0). |
| `observerRecordsLiveEvents` | Start bundle first (empty store → projectors reach head immediately), then publish; verify the observer picks up live events. |
| `livePublishIsPickedUpByRunningProjector` | Publish events while the bundle is already running; verify real-time projector delivery. |

### `ConnectivityIT` (3 tests)

Bundle-to-broker handshake, multi-bundle registration, and graceful shutdown.

| Test | Objective |
|---|---|
| `bundleRegistersAndBecomesAvailableAfterProjectorsReachHead` | Empty store → projectors reach head immediately → bundle sends enable signal → broker marks it available. Verifies the two-phase startup gate. |
| `bundleRecoversAfterBrokerRestart` | Connect to a second broker, verify processing, then close that broker. The bundle's `EngineSupervisor` must not crash — `isShuttingDown()` stays false. |
| `multipleBundlesCanConnectToBrokerSimultaneously` | Start two bundles with different IDs against the same broker. Both must become available and appear in the broker's cluster view. |

### `AbstractJdbcConsumerIT` / `PostgresConsumerIT` / `MysqlConsumerIT` (gated — 23 tests each)

Same consumer scenarios as `InMemoryConsumerIT` but backed by real JDBC state stores
(`JdbcConsumerStateStore`, `JdbcSagaStateStore`, etc.) against Testcontainers-managed
databases. Only run when `EVENTO_RUN_JDBC_IT=true`.
