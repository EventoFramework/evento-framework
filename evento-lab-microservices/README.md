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
evento-lab-ms-saga/      Saga process manager (payment flow)
evento-lab-ms-observer/  Fire-and-forget reactions + notification service
evento-lab-ms-it/        Integration test harness (no Docker, no Spring context)
```

### `evento-lab-ms-api`

Payload types shared across all services. No framework logic — just DTOs.

| Package | Types |
|---|---|
| `api.command` | `CreateOrderCommand` (DomainCommand), `AddOrderItemCommand`, `RemoveOrderItemCommand`, `CompleteOrderCommand` · `ConfirmOrderCommand`, `CancelOrderCommand`, `OpenPaymentIntentCommand`, `SendNotificationCommand` (ServiceCommand) |
| `api.event`   | `OrderCreatedEvent` (DomainEvent), `OrderItemAddedEvent`, `OrderItemRemovedEvent` · `OrderCompletedEvent`, `OrderConfirmedEvent`, `OrderCancelledEvent`, `PaymentIntentOpenedEvent`, `PaymentStatusChangedEvent`, `NotificationSentEvent` (ServiceEvent) |
| `api.query`   | `FindOrderByIdQuery`, `ListOrdersQuery` |
| `api.view`    | `OrderView` (orderId, description, quantity, status, cancelled, items, paymentStatus, context), `OrderItemView` (itemId, name, price, quantity) |

### `evento-lab-ms-command`

Handles the write side of the Order domain.

- `OrderAggregate` (`@Aggregate`, snapshotFrequency=5) — full lifecycle: `CreateOrderCommand` → `OrderCreatedEvent`, `AddOrderItemCommand` → `OrderItemAddedEvent`, `RemoveOrderItemCommand` → `OrderItemRemovedEvent`
- `OrderService` (`@Service`) — `ConfirmOrderCommand` → `OrderConfirmedEvent`, `CancelOrderCommand` → `OrderCancelledEvent`, `CompleteOrderCommand` → `OrderCompletedEvent`
- `PaymentService` (`@Service`) — `OpenPaymentIntentCommand` → `PaymentIntentOpenedEvent`
- `EventoConfiguration` wires an `EventoBundle` pointing at `evento.server.host/port` from `application.properties`

### `evento-lab-ms-query`

Builds and serves the read model.

- `OrderProjector` (`@Projector(version=1)`) — handles all order events; builds and updates `OrderViewStore` entries including the `items` list and `paymentStatus`
- `OrderProjection` (`@Projection`) — handles `FindOrderByIdQuery` and `ListOrdersQuery`
- `OrderViewStore` — static `ConcurrentHashMap`; the assertion surface in IT tests. `reset()` clears between tests; `getAll()` returns an unmodifiable snapshot.

### `evento-lab-ms-saga`

Coordinates the payment flow across service boundaries.

- `OrderSaga` (`@Saga(version=1)`) — starts on `OrderCompletedEvent` (association: `orderId`), generates a `paymentIntentId`, records `PAYMENT_PENDING` in `MsSagaStore`, then sends `OpenPaymentIntentCommand` via `CommandGateway`. Transitions on `PaymentStatusChangedEvent`: `SUCCESS` → sends `ConfirmOrderCommand` + records `PAYMENT_SUCCESS`; `FAILED` → sends `CancelOrderCommand` + records `PAYMENT_FAILED`.
- `OrderSagaState` extends `SagaState`; carries `orderId`, `paymentIntentId`, `phase`
- `MsSagaStore` — static store for IT assertions: `record(orderId, status)` / `getStatus(orderId)`

### `evento-lab-ms-observer`

Handles fire-and-forget reactions to domain events.

- `OrderObserver` (`@Observer(version=1)`) — reacts to `OrderCreatedEvent`, `OrderConfirmedEvent`, `OrderCancelledEvent`, `OrderCompletedEvent`; records a token in `MsObservedEvents` and dispatches a `SendNotificationCommand` to the notification service via `CommandGateway`
- `NotificationService` (`@Service`) — handles `SendNotificationCommand`, records `channel:orderId:message` in `MsNotificationLog`, emits `NotificationSentEvent`
- `MsObservedEvents` — static `CopyOnWriteArrayList`; tokens like `"created:<id>"`, `"confirmed:<id>"`
- `MsNotificationLog` — static log of sent notifications; format `"channel:orderId:message"`

## Test infrastructure (`evento-lab-ms-it`)

The IT module starts multiple `EventoBundle` instances in the same JVM against a shared
in-process broker. No Spring context is started; no Docker is needed.

| Class | Role |
|---|---|
| `MsEmbeddedBroker` | Starts a real v2 broker (`BusLifecycle` + `NettyServerTransport`) on a random port |
| `MsTestEventStore` | In-process event journal; handles `EventFetchRequest` + `EventLastSequenceNumberRequest`. `publish(DomainEvent, aggregateId)` wraps in `DomainEventMessage`; `publishServiceEvent(ServiceEvent)` wraps in `ServiceEventMessage`; `publishWithContext(event, aggregateId, ctx)` tags by geographic context; `publishCorrupted(eventName, aggregateId)` creates a `ServiceEventMessage(null)` with null `objectClass` for regression testing. |
| `MsHarness` | Fluent builder: `.withQueryBundle()` / `.withSagaBundle()` / `.withObserverBundle()` / `.withCommandBundle()` / `.withQueryBundleForContext(ctx)`. Each scans the corresponding Spring Boot app's package and starts an `EventoBundle` against the shared broker. `close()` stops all bundles, the event store, and the broker. |

Static singleton stores (`OrderViewStore`, `MsSagaStore`, `MsObservedEvents`, `MsNotificationLog`)
act as cross-bundle assertion surfaces: because all bundles run in the same JVM during tests,
handlers in any bundle write to the same static state that the IT test reads.

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

No Docker required. All 22 tests complete in under 120 seconds on a laptop.

## Test inventory

### `MsConsumerLifecycleIT` (3 tests)

Multi-bundle fan-out and independent consumer delivery.

| Test | Objective |
|---|---|
| `allThreeConsumerBundlesReceivePublishedEvents` | Start query + saga + observer bundles, publish one `OrderCreatedEvent`. All three must process it independently. Verifies fan-out from the broker to three separate consumer engines. |
| `queryBundleProjectsMultipleEventsCorrectly` | Publish 5 events; verify all 5 appear in `OrderViewStore` with correct field values. Exercises the projector catch-up path under load. |
| `observerAndProjectorReceiveSameEventIndependently` | Start query + observer (no saga). Publish one event. Both must process it. Verifies two consumer types with separate checkpoints don't interfere. |

### `MsSagaIT` (2 tests)

Saga lifecycle — state transitions driven by correlated events.

| Test | Objective |
|---|---|
| `sagaHappyPath_createdThenConfirmed` | Publish `OrderCreatedEvent` → saga starts, `MsSagaStore` = `"CREATED"`. Publish `OrderConfirmedEvent` → `MsSagaStore` = `"CONFIRMED"`. Verifies the full happy-path saga flow. |
| `sagaCompensation_createdThenCancelled` | Same setup; publish `OrderCancelledEvent` as second event. `MsSagaStore` must reach `"CANCELLED"`. Verifies the compensation branch. |

### `MsReconnectIT` (2 tests)

Bundle resilience when the broker closes.

| Test | Objective |
|---|---|
| `queryBundleReconnectsAfterBrokerDropAndResumesProcessing` | Start broker + event store + query bundle; verify processing; close cleanly. Verifies `EngineSupervisor.stop()` completes without hanging. |
| `bundleDoesNotCrashWhenBrokerCloses` | Start broker + bundle; wait; stop bundle; close broker. Asserts `isShuttingDown() == true` — clean terminal state. |

### `MsOrderLifecycleIT` (2 tests)

Full order lifecycle: create, add items, remove item, complete.

| Test | Objective |
|---|---|
| `fullOrderLifecycle_createAddItemsRemoveItemComplete` | Publish `OrderCreatedEvent` → `OrderItemAddedEvent` × 2 → `OrderItemRemovedEvent` → `OrderCompletedEvent`. Verifies item list in `OrderViewStore` reflects each mutation and final status is `"COMPLETED"`. |
| `multipleOrdersProjectedIndependently` | Three orders with different item sets published in interleaved order. All three must land in `OrderViewStore` with correct item counts. |

### `MsPaymentSagaIT` (2 tests)

Payment saga — opens a payment intent on order completion, confirms or cancels based on payment outcome.

| Test | Objective |
|---|---|
| `paymentSuccess_sagaOpensIntentAndConfirmsOrder` | Publish `OrderCompletedEvent` → saga opens, `MsSagaStore` = `"PAYMENT_PENDING"`. Publish `PaymentStatusChangedEvent(SUCCESS)` → `"PAYMENT_SUCCESS"`. |
| `paymentFailure_sagaOpensIntentAndCancelsOrder` | Same start; publish `PaymentStatusChangedEvent(FAILED)` → `"PAYMENT_FAILED"`. Verifies compensation branch. |

### `MsNotificationIT` (3 tests)

Notification service and observer notification flow.

| Test | Objective |
|---|---|
| `observerSendsNotificationOnOrderCreated` | Publish `OrderCreatedEvent` → observer records token and dispatches `SendNotificationCommand` → `MsNotificationLog` contains `"EMAIL:<id>:Order created: <id>"`. |
| `notificationServiceHandlesSendNotificationCommand` | Publish two creation events. Both must produce notification log entries. Verifies concurrent notification dispatch. |
| `confirmationEventTriggersEmailNotification` | Publish `OrderConfirmedEvent` (service event) → `MsNotificationLog` must contain the confirmation notification. |

### `MsMultiContextIT` (2 tests)

Parallel context consumers with isolated checkpoints.

| Test | Objective |
|---|---|
| `parallelContextConsumers_ITEventsOnlyReachITProjector` | Start IT and UK projector bundles. Publish 2 IT + 2 UK events. Each projector must project its own context's events; `OrderView.context` must match. |
| `contextBundlesProcessEventsInParallel` | Interleave 3 IT + 3 UK events. Both context projectors must each receive exactly 3 events, all with the correct context tag. |

### `MsRttIT` (3 tests)

Round-trip time and stress scenarios.

| Test | Objective |
|---|---|
| `singleEventRtt_projectorProcessesWithinThreshold` | Publish 1 event; measure end-to-end latency from publish to `OrderViewStore` entry. Must complete under 3 s. |
| `stressTest_100OrdersAllProjected` | Publish 100 `OrderCreatedEvent`s; all must appear in `OrderViewStore`. Reports throughput (events/s). |
| `concurrentBundlesFanOut_allConsumersReceive` | Start query + observer bundles; publish 20 events; all 20 must reach both `OrderViewStore` and `MsObservedEvents`. |

### `MsNullPayloadRegressionIT` (3 tests)

Regression tests for the `Message.getPayloadName()` null-objectClass NPE fix, triggered by
the Iris platform `testImpersonate` test hitting a PUT endpoint whose response included a
`ServiceEventMessage` with a null `objectClass` (old or null-payload DB record).

| Test | Objective |
|---|---|
| `nullObjectClass_getEventName_returnsNullWithoutNpe` | Directly verifies the fix: `ServiceEventMessage(null).getEventName()` / `getPayloadName()` / `getType()` return `null` instead of throwing NPE. |
| `corruptedEventInStore_pipelineContinuesDelivery` | Publish a corrupted event (unknown event name, null objectClass) followed by a normal `OrderCreatedEvent`. Consumer must skip the corrupted event and deliver the normal one. |
| `mixedCorruptedAndNormalEvents_allConsumersRemainOperational` | Interleave 2 corrupted events with normal domain + service events. Observer and notification service must process all normal events; bundle must stay available throughout. |