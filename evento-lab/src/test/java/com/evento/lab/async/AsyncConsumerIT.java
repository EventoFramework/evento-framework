package com.evento.lab.async;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.messaging.consumer.CheckpointMode;
import com.evento.common.messaging.consumer.ConsumerExecutor;
import com.evento.common.messaging.consumer.ConsumerExecutors;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.api.event.OrderUpdatedEvent;
import com.evento.lab.support.EmbeddedBroker;
import com.evento.lab.support.TestEventStoreBundleClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end coverage for {@code @EventHandler(executor = "...")} over a real broker.
 *
 * @see com.evento.common.messaging.consumer.ConsumerExecutor
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class AsyncConsumerIT {

    private static final int CAPACITY = 4;
    private static final String BUNDLE_ID = "lab-async-bundle";

    private EmbeddedBroker broker;
    private TestEventStoreBundleClient eventStore;
    private EventoBundle bundle;
    private ConsumerExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        AsyncLabStore.reset();
        TransactionTrackingInterceptor.reset();
        broker = new EmbeddedBroker();
        eventStore = new TestEventStoreBundleClient(broker.port());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (bundle != null) {
            bundle.getEngineSupervisor().stop(Duration.ofSeconds(15));
            bundle = null;
        }
        if (executor != null) { executor.shutdown(Duration.ofSeconds(5)); executor = null; }
        if (eventStore != null) { eventStore.close(); eventStore = null; }
        if (broker != null) { broker.close(); broker = null; }
    }

    private void startBundle() throws Exception {
        executor = ConsumerExecutors.virtual(AsyncLabProjector.EXECUTOR, CAPACITY);
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(AsyncLabProjector.class.getPackage())
                .setBundleId(BUNDLE_ID)
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .setMessageHandlerInterceptor(new TransactionTrackingInterceptor())
                .addConsumerExecutor(executor)
                .start();
    }

    private void awaitBundleAvailable() {
        await().atMost(60, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable(BUNDLE_ID));
    }

    private void publishOrders(int count, String prefix) {
        for (int i = 0; i < count; i++) {
            var id = prefix + "-" + i;
            eventStore.publish(new OrderCreatedEvent(id, "desc-" + i, i), id);
        }
    }

    // --- Parallelism --------------------------------------------------------

    @Test
    void asyncHandlersRunConcurrentlyUpToTheExecutorCapacity() throws Exception {
        AsyncLabStore.handlerDelayMillis = 300;
        publishOrders(12, "par");

        startBundle();

        await().atMost(60, TimeUnit.SECONDS)
                .until(() -> AsyncLabStore.applied.size() == 12);

        // Sequentially this would be one at a time; the executor lets CAPACITY overlap.
        assertThat(AsyncLabStore.peakConcurrent.get())
                .as("peak concurrency should reach the executor capacity")
                .isEqualTo(CAPACITY);
    }

    // --- The bundle-enable race --------------------------------------------

    @Test
    void bundleIsNotEnabledUntilAsyncHandlersHaveFinished() throws Exception {
        // Slow handlers + a backlog present before start: the fetch loop reaches head long
        // before the work is applied. If head-reached did not drain, the bundle would be
        // enabled — and start serving queries — against an unaligned read model.
        //
        // The delay must comfortably exceed the enable round-trip, otherwise the last
        // batch finishes during that latency and the test passes even without the drain.
        // With capacity 4 and 8 events, the fetch loop returns once events 5-8 have merely
        // *started*, leaving a full 1.5 s of work outstanding at that moment.
        AsyncLabStore.handlerDelayMillis = 1_500;
        publishOrders(8, "align");

        startBundle();
        awaitBundleAvailable();

        assertThat(AsyncLabStore.applied)
                .as("every event must be applied at the moment the bundle becomes available")
                .hasSize(8);
        assertThat(AsyncLabStore.concurrent.get()).isZero();
    }

    // --- Interceptor-managed transactions -----------------------------------

    @Test
    void interceptorAndHandlerShareOneThreadSoThreadLocalTransactionsWork() throws Exception {
        publishOrders(6, "tx");

        startBundle();
        await().atMost(60, TimeUnit.SECONDS)
                .until(() -> AsyncLabStore.applied.size() == 6);

        for (int i = 0; i < 6; i++) {
            var id = "tx-" + i;
            var before = AsyncLabStore.threadNames.get(id + ":before");
            var handler = AsyncLabStore.threadNames.get(id + ":handler");
            var after = AsyncLabStore.threadNames.get(id + ":after");

            assertThat(before).as("before-hook thread for %s", id).isNotNull();
            // The invariant that makes ThreadLocal-bound transactions work unchanged
            // under parallel dispatch.
            assertThat(handler).as("handler thread for %s", id).isEqualTo(before);
            assertThat(after).as("after-hook thread for %s", id).isEqualTo(before);
            assertThat(before)
                    .as("handling must happen on the executor, not the fetch loop")
                    .startsWith("evento-consumer-" + AsyncLabProjector.EXECUTOR);

            // The transaction bound in before-hook was visible inside the handler.
            assertThat(AsyncLabStore.transactionSeenByHandler.get(id)).startsWith("tx-");
        }

        assertThat(TransactionTrackingInterceptor.opened.get()).isEqualTo(6);
        assertThat(TransactionTrackingInterceptor.committed.get()).isEqualTo(6);
        assertThat(TransactionTrackingInterceptor.rolledBack.get()).isZero();
    }

    @Test
    void failingAsyncHandlerRollsBackAndDeadLetters() throws Exception {
        AsyncLabStore.failFor.add("fail-0");
        publishOrders(3, "fail");

        startBundle();
        awaitBundleAvailable();

        await().atMost(60, TimeUnit.SECONDS)
                .until(() -> AsyncLabStore.applied.size() == 2);

        assertThat(TransactionTrackingInterceptor.rolledBack.get())
                .as("retry = 0 under an executor means exactly one attempt")
                .isEqualTo(1);

        var consumer = bundle.getEngineSupervisor().getProjectorEngines().iterator().next();
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(consumer.getDeadEventQueue()).hasSize(1));
        // The DLQ write happens after onException unbound the transaction, so it commits.
        assertThat(consumer.getDeadEventQueue().iterator().next().getAggregateId()).isEqualTo("fail-0");
    }

    // --- Inline barrier -----------------------------------------------------

    @Test
    void inlineHandlerWaitsForEarlierAsyncEvents() throws Exception {
        // OrderCreated is async and slow; the following OrderUpdated is inline. The
        // inline handler must not overtake it.
        AsyncLabStore.handlerDelayMillis = 400;
        eventStore.publish(new OrderCreatedEvent("barrier-0", "d", 1), "barrier-0");
        eventStore.publish(new OrderUpdatedEvent("barrier-0", "d2", 2), "barrier-0");

        startBundle();
        await().atMost(60, TimeUnit.SECONDS)
                .until(() -> AsyncLabStore.applied.size() == 2);

        assertThat(AsyncLabStore.applied).containsExactly("barrier-0", "inline:barrier-0");
    }

    // --- Graceful shutdown --------------------------------------------------

    @Test
    void gracefulStopDrainsInFlightHandlers() throws Exception {
        var gate = new CountDownLatch(1);
        AsyncLabStore.gate = gate;
        publishOrders(CAPACITY, "drain");

        startBundle();

        // Hold every permit, then stop while the tasks are mid-flight.
        await().atMost(60, TimeUnit.SECONDS)
                .until(() -> AsyncLabStore.concurrent.get() == CAPACITY);

        // Each handler still has real work left after the gate opens, so a stop() that
        // failed to drain would return before they finished.
        AsyncLabStore.handlerDelayMillis = 1_000;

        var stopper = Thread.ofPlatform().start(() ->
                bundle.getEngineSupervisor().stop(Duration.ofSeconds(20)));
        Thread.sleep(200);
        gate.countDown();
        stopper.join(30_000);
        assertThat(stopper.isAlive()).isFalse();

        // These events were checkpointed when their task started; dropping them on a
        // clean stop would lose them silently.
        assertThat(AsyncLabStore.applied)
                .as("in-flight handlers must complete before stop() returns")
                .hasSize(CAPACITY);
        bundle = null; // already stopped
    }

    // --- Ordering and checkpoint mode ---------------------------------------

    @Test
    void aPartitionedExecutorKeepsSameAggregateEventsInOrder() throws Exception {
        // Three events per aggregate, two aggregates, interleaved in the stream. The
        // handler sleeps unevenly so an unpartitioned executor would reorder them.
        for (int i = 0; i < 3; i++) {
            eventStore.publish(new OrderCreatedEvent("ord-a", "d", i), "ord-a");
            eventStore.publish(new OrderCreatedEvent("ord-b", "d", i), "ord-b");
        }
        AsyncLabStore.handlerDelayMillis = 40;

        executor = ConsumerExecutors.partitioned(AsyncLabProjector.EXECUTOR, 8);
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(AsyncLabProjector.class.getPackage())
                .setBundleId(BUNDLE_ID)
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .addConsumerExecutor(executor)
                .start();
        awaitBundleAvailable();

        assertThat(AsyncLabStore.appliedPerAggregate.get("ord-a")).containsExactly(0L, 1L, 2L);
        assertThat(AsyncLabStore.appliedPerAggregate.get("ord-b")).containsExactly(0L, 1L, 2L);
    }

    @Test
    void watermarkModeCheckpointsOnlyCompletedWork() throws Exception {
        publishOrders(4, "wm");

        executor = ConsumerExecutors.virtual(AsyncLabProjector.EXECUTOR, CAPACITY);
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(AsyncLabProjector.class.getPackage())
                .setBundleId(BUNDLE_ID)
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .addConsumerExecutor(executor)
                .setCheckpointMode(CheckpointMode.WATERMARK)
                .start();
        awaitBundleAvailable();

        var consumer = bundle.getEngineSupervisor().getProjectorEngines().iterator().next();
        assertThat(consumer.getCheckpointMode()).isEqualTo(CheckpointMode.WATERMARK);

        // Everything finished, so the watermark caught up to the frontier: under WATERMARK
        // the checkpoint is a statement about completion, not dispatch.
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(consumer.getLastConsumedEvent()).isEqualTo(4L));
        assertThat(AsyncLabStore.applied).hasSize(4);
    }

    // --- Consumer status (Phase 2) ------------------------------------------

    @Test
    void consumerStatusReportsTheExecutorsTheConsumerDependsOn() throws Exception {
        publishOrders(3, "status");

        startBundle();
        awaitBundleAvailable();

        var consumer = bundle.getEngineSupervisor().getProjectorEngines().iterator().next();
        var status = consumer.toConsumerStatus();

        assertThat(status.getAsyncExecutors()).containsExactly(AsyncLabProjector.EXECUTOR);
        assertThat(status.getAsyncInFlight()).isZero();
        assertThat(status.getAsyncTransientFailures()).isZero();
    }

    // --- Discovery metadata (Phase 3) ---------------------------------------

    @Test
    void discoveryPublishesTheExecutorNamePerHandler() throws Exception {
        // Capture the rich discovery notification as it actually arrives over the socket,
        // so this covers CBOR round-tripping of the new field, not just the local builder.
        var discovered = new java.util.concurrent.CopyOnWriteArrayList<
                com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler>();
        broker.eventBus().subscribe(e -> {
            if (e instanceof com.evento.server.bus.event.BusEvent.BundleDiscovered d) {
                discovered.addAll(d.discovery().handlers());
            }
        });

        startBundle();
        awaitBundleAvailable();
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> discovered.stream().anyMatch(h -> AsyncLabProjector.class.getSimpleName()
                        .equals(h.getComponentName())));

        var handlers = discovered;
        var async = handlers.stream()
                .filter(h -> "OrderCreatedEvent".equals(h.getHandledPayload()))
                .findFirst().orElseThrow();
        assertThat(async.getExecutor()).isEqualTo(AsyncLabProjector.EXECUTOR);

        var inline = handlers.stream()
                .filter(h -> "OrderUpdatedEvent".equals(h.getHandledPayload()))
                .findFirst().orElseThrow();
        assertThat(inline.getExecutor()).isEmpty();
    }

    /**
     * Guards the trap the {@code executor} field walked into: the transport codec rejects
     * unknown properties, so any derived getter added to a wire DTO silently kills the
     * whole discovery notification — the bundle keeps running and only its handler
     * metadata vanishes. Asserting a populated discovery arrived at all is what catches it.
     */
    @Test
    void discoveryNotificationDecodesWithTheNewField() throws Exception {
        var discoveredBundles = new java.util.concurrent.CopyOnWriteArrayList<String>();
        broker.eventBus().subscribe(e -> {
            if (e instanceof com.evento.server.bus.event.BusEvent.BundleDiscovered d
                    && !d.discovery().handlers().isEmpty()) {
                discoveredBundles.add(d.node().bundleId());
            }
        });

        startBundle();
        awaitBundleAvailable();

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(discoveredBundles).contains(BUNDLE_ID));
    }

    // --- Start-up validation ------------------------------------------------

    @Test
    void unknownExecutorNameFailsBundleStartUp() {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                bundle = EventoBundle.Builder.builder()
                        .setBasePackage(AsyncLabProjector.class.getPackage())
                        .setBundleId(BUNDLE_ID + "-invalid")
                        .setEventoServerMessageBusConfiguration(
                                new EventoServerMessageBusConfiguration(
                                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                        .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                        // deliberately NOT registering "lab-async"
                        .start());

        assertThat(rootCause(thrown)).hasMessageContaining(AsyncLabProjector.EXECUTOR);
    }

    private static Throwable rootCause(Throwable t) {
        var cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur;
    }

    @SuppressWarnings("unused")
    private static List<String> unused() {
        return List.of();
    }
}
