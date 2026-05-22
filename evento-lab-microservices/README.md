# evento-lab-microservices

Multi-module project that demonstrates the RECQ (Reactive Event Command Query) microservices
pattern with Evento Framework. Each RECQ concern lives in its own deployable Spring Boot
application, mirroring a real production split where command, query, saga, and observer
services are independently deployable and scalable.

## Module overview

```
evento-lab-ms-api/       Shared payload library — commands, events, queries, views
evento-lab-ms-command/   Aggregate + service handlers (write side)
evento-lab-ms-query/     Projector + projection handlers (read side)
evento-lab-ms-saga/      Saga process manager
evento-lab-ms-observer/  Fire-and-forget side-effects
evento-lab-ms-it/        Integration test harness (no Docker, no Spring context)
```

### `evento-lab-ms-api`

Payload types shared across all services. No framework logic — just DTOs.

| Package | Types |
|---|---|
| `api.command` | `CreateOrderCommand` (DomainCommand), `ConfirmOrderCommand`, `CancelOrderCommand` (ServiceCommand) |
| `api.event`   | `OrderCreatedEvent` (DomainEvent), `OrderConfirmedEvent`, `OrderCancelledEvent` (ServiceEvent) |
| `api.query`   | `FindOrderByIdQuery`, `ListOrdersQuery` |
| `api.view`    | `OrderView` |

### `evento-lab-ms-command`

Handles the write side of the Order domain.

- `OrderAggregate` (`@Aggregate`, snapshotFrequency=5) — handles `CreateOrderCommand`, emits `OrderCreatedEvent`
- `OrderService` (`@Service`) — handles `ConfirmOrderCommand` → `OrderConfirmedEvent`, `CancelOrderCommand` → `OrderCancelledEvent`
- `EventoConfiguration` wires an `EventoBundle` pointing at `evento.server.host/port` from `application.properties`

### `evento-lab-ms-query`

Builds and serves the read model.

- `OrderProjector` (`@Projector(version=1)`) — handles all three events, updates `OrderViewStore`
- `OrderProjection` (`@Projection`) — handles `FindOrderByIdQuery` and `ListOrdersQuery`
- `OrderViewStore` — static `ConcurrentHashMap`; also serves as the assertion surface in IT tests

### `evento-lab-ms-saga`

Coordinates multi-step business processes across service boundaries.

- `OrderSaga` (`@Saga(version=1)`) — starts on `OrderCreatedEvent`, transitions on `OrderConfirmedEvent` (→ CONFIRMED) and `OrderCancelledEvent` (→ CANCELLED)
- `OrderSagaState` extends `SagaState`; carries `orderId` and `status`
- `MsSagaStore` — static store for IT assertions; `record(orderId, status)` / `getStatus(orderId)`

### `evento-lab-ms-observer`

Handles fire-and-forget reactions to domain events (e.g. notifications, audit logs).

- `OrderObserver` (`@Observer(version=1)`) — records a string token (`"created:<id>"` etc.) into `MsObservedEvents` for every order event
- `MsObservedEvents` — static `CopyOnWriteArrayList`; assertion surface in IT tests

## Test infrastructure (`evento-lab-ms-it`)

The IT module starts multiple `EventoBundle` instances in the same JVM against a shared
in-process broker. No Spring context is started; no Docker is needed.

| Class | Role |
|---|---|
| `MsEmbeddedBroker` | Starts a real v2 broker (`BusLifecycle` + `NettyServerTransport`) on a random port |
| `MsTestEventStore` | In-process event journal; handles `EventFetchRequest` + `EventLastSequenceNumberRequest`. `publish(DomainEvent, aggregateId)` wraps in `DomainEventMessage`; `publishServiceEvent(ServiceEvent)` wraps in `ServiceEventMessage` so `@Saga`/`@Observer` handlers routing on `ServiceEvent` are reached correctly. |
| `MsHarness` | Fluent builder: `.withQueryBundle()` / `.withSagaBundle()` / `.withObserverBundle()` / `.withCommandBundle()`. Each scans the corresponding Spring Boot app's package and starts an `EventoBundle` against the shared broker. `close()` stops all bundles, the event store, and the broker. |

Static singleton stores (`OrderViewStore`, `MsSagaStore`, `MsObservedEvents`) act as
cross-bundle assertion surfaces: because all bundles run in the same JVM during tests,
a projector in the query bundle writes to the same `OrderViewStore` that the IT test reads.

> **Note on aggregate commands.** `DecoratedDomainCommandMessage` decoration requires an
> event-store service RPC not wired in `MsTestEventStore`. The IT suite exercises the
> consumer pipeline (projector, saga, observer) by publishing events directly; command
> dispatch through `OrderAggregate` / `OrderService` is covered by deploying the command
> service against a real Evento Server.

## Running the tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-lab-microservices:evento-lab-ms-it:test
```

No Docker required. All 7 tests complete in under 90 seconds on a laptop.

## Test inventory

### `MsConsumerLifecycleIT` (3 tests)

Multi-bundle fan-out and independent consumer delivery.

| Test | Objective |
|---|---|
| `allThreeConsumerBundlesReceivePublishedEvents` | Start query + saga + observer bundles, publish one `OrderCreatedEvent`. All three must process it independently: `OrderViewStore` populated, `MsObservedEvents` updated. Verifies fan-out from the broker to three separate consumer engines. |
| `queryBundleProjectsMultipleEventsCorrectly` | Publish 5 events; verify all 5 appear in `OrderViewStore` with correct field values. Exercises the projector catch-up path under load. |
| `observerAndProjectorReceiveSameEventIndependently` | Start query + observer (no saga). Publish one event. Both must process it. Verifies that two different consumer types with separate checkpoints don't interfere. |

### `MsSagaIT` (2 tests)

Saga lifecycle — state transitions driven by correlated events.

| Test | Objective |
|---|---|
| `sagaHappyPath_createdThenConfirmed` | Publish `OrderCreatedEvent` (domain) → saga starts, `MsSagaStore` = "CREATED". Publish `OrderConfirmedEvent` (service) → saga transitions, `MsSagaStore` = "CONFIRMED". Verifies the full happy-path saga flow and that `publishServiceEvent()` routes correctly to a running saga. |
| `sagaCompensation_createdThenCancelled` | Same setup, but publish `OrderCancelledEvent` as the second event. `MsSagaStore` must reach "CANCELLED". Verifies the compensation branch of the saga. |

### `MsReconnectIT` (2 tests)

Bundle resilience when the broker closes.

| Test | Objective |
|---|---|
| `queryBundleReconnectsAfterBrokerDropAndResumesProcessing` | Start broker + event store + query bundle; verify processing; then close all three cleanly. Verifies that `EngineSupervisor.stop()` completes without hanging after the broker is gone. |
| `bundleDoesNotCrashWhenBrokerCloses` | Start broker + bundle; wait for availability; stop bundle; then close broker. Asserts `isShuttingDown() == true` — the supervisor reached a clean terminal state rather than crashing. |
