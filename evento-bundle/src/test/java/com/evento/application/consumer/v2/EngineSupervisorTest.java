package com.evento.application.consumer.v2;

import com.evento.application.performance.TracingAgent;
import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.EventFetchRequest;
import com.evento.common.messaging.consumer.EventFetchResponse;
import com.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import com.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import com.evento.common.messaging.consumer.v2.ConsumerProcessor;
import com.evento.common.messaging.consumer.v2.impl.InMemoryConsumerLock;
import com.evento.common.messaging.consumer.v2.impl.InMemoryConsumerStateStore;
import com.evento.common.messaging.consumer.v2.impl.InMemoryDeadEventQueue;
import com.evento.common.messaging.consumer.v2.impl.InMemoryDedupeStore;
import com.evento.common.messaging.consumer.v2.impl.InMemorySagaStateStore;
import com.evento.common.modeling.bundle.types.ComponentType;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lifecycle + lookup tests for {@link EngineSupervisor}. The engines are real
 * instances driven through their {@code run()} loops, but with empty fetch
 * responses + empty handler maps so the per-event dispatch never fires —
 * what we want to assert is the supervisor's framing: start, stop within
 * deadline, look up engines by id + component type.
 *
 * <p>Per-event behaviour (lock contention, DLQ, optimistic checkpoint commits)
 * is already covered by {@code ConsumerProcessorTest} in evento-common — no
 * point in re-asserting it here.
 */
class EngineSupervisorTest {

    private static final String BUNDLE = "test-bundle";

    @Test
    void stopExitsEnginesWithinDeadline() throws Exception {
        var supervisor = new EngineSupervisor();
        var processor = newProcessor();

        supervisor.addProjector(new ProjectorEngine(
                BUNDLE, "Proj", 1, "ctx",
                supervisor.shutdownSupplier(),
                processor.processor(),
                processor.stateStore(),
                processor.dlq(),
                new HashMap<>(),
                new DispatchContext(new TracingAgent(BUNDLE, 1), (c, m) -> null,
                        new com.evento.application.manager.LogTracesMessageHandlerInterceptor()),
                /* fetchSize */ 11, // 11-0 > 10 triggers a short Sleep.apply(11) each loop
                /* fetchDelay */ 100,
                new AtomicInteger(1),
                () -> {}));

        supervisor.startProjectorEngines();
        // Give the engine a few iterations to spin.
        Thread.sleep(50);

        long start = System.nanoTime();
        supervisor.stop(Duration.ofSeconds(2));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(supervisor.isShuttingDown()).isTrue();
        assertThat(elapsedMs)
                .as("stop should return quickly because Sleep.apply is interruptible")
                .isLessThan(1500L);
    }

    @Test
    void stopIsIdempotent() {
        var supervisor = new EngineSupervisor();
        supervisor.stop(Duration.ofMillis(100)); // no engines, no executor
        supervisor.stop(Duration.ofMillis(100)); // second call must be a no-op
        assertThat(supervisor.isShuttingDown()).isTrue();
    }

    @Test
    void findConsumerRoutesByComponentType() {
        var supervisor = new EngineSupervisor();
        var processor = newProcessor();

        var dispatchCtx = new DispatchContext(new TracingAgent(BUNDLE, 1), (c, m) -> null,
                new com.evento.application.manager.LogTracesMessageHandlerInterceptor());
        var p = new ProjectorEngine(
                BUNDLE, "ProjA", 1, "ctx",
                supervisor.shutdownSupplier(),
                processor.processor(), processor.stateStore(), processor.dlq(),
                new HashMap<>(), dispatchCtx,
                10, 100, new AtomicInteger(1), () -> {});
        var s = new SagaEngine(
                BUNDLE, "SagaA", 1, "ctx",
                supervisor.shutdownSupplier(),
                processor.processor(), processor.stateStore(), processor.dlq(),
                new HashMap<>(), dispatchCtx,
                10, 100);
        var o = new ObserverEngine(
                BUNDLE, "ObsA", 1, "ctx",
                supervisor.shutdownSupplier(),
                processor.processor(), processor.stateStore(), processor.dlq(),
                new HashMap<>(), dispatchCtx,
                10, 100);

        supervisor.addProjector(p);
        supervisor.addSaga(s);
        supervisor.addObserver(o);

        assertThat(supervisor.findConsumer(p.getConsumerId(), ComponentType.Projector)).hasValueSatisfying(c ->
                assertThat(c).isSameAs(p));
        assertThat(supervisor.findConsumer(s.getConsumerId(), ComponentType.Saga)).hasValueSatisfying(c ->
                assertThat(c).isSameAs(s));
        assertThat(supervisor.findConsumer(o.getConsumerId(), ComponentType.Observer)).hasValueSatisfying(c ->
                assertThat(c).isSameAs(o));

        // Wrong component type for an existing id → empty.
        assertThat(supervisor.findConsumer(p.getConsumerId(), ComponentType.Saga)).isEmpty();
        // Unknown id → empty.
        assertThat(supervisor.findConsumer("nope", ComponentType.Projector)).isEmpty();
    }

    @Test
    void shutdownSupplierReflectsState() {
        var supervisor = new EngineSupervisor();
        assertThat(supervisor.shutdownSupplier().get()).isFalse();
        supervisor.stop(Duration.ofMillis(50));
        assertThat(supervisor.shutdownSupplier().get()).isTrue();
    }

    // --- helpers -----------------------------------------------------------

    private record ProcessorPack(ConsumerProcessor processor,
                                 com.evento.common.messaging.consumer.v2.ConsumerStateStore stateStore,
                                 com.evento.common.messaging.consumer.v2.DeadEventQueue dlq) {}

    private static ProcessorPack newProcessor() {
        var stateStore = new InMemoryConsumerStateStore();
        var dlq = new InMemoryDeadEventQueue();
        var processor = ConsumerProcessor.builder()
                .eventoServer(new EmptyServer())
                .lock(new InMemoryConsumerLock())
                .stateStore(stateStore)
                .sagaStateStore(new InMemorySagaStateStore())
                .deadEventQueue(dlq)
                .dedupeStore(new InMemoryDedupeStore())
                .observerExecutor(Runnable::run)
                .timeoutMillis(5_000)
                .build();
        return new ProcessorPack(processor, stateStore, dlq);
    }

    /** EventoServer stub that always returns an empty fetch + head=0. */
    static final class EmptyServer implements EventoServer {
        @Override public void send(Serializable message) {}
        @SuppressWarnings("unchecked")
        @Override
        public <T extends Serializable> CompletableFuture<T> request(Serializable request, long timeout, TimeUnit unit) {
            if (request instanceof EventFetchRequest) {
                return CompletableFuture.completedFuture((T) new EventFetchResponse(new java.util.ArrayList<>()));
            }
            if (request instanceof EventLastSequenceNumberRequest) {
                var r = new EventLastSequenceNumberResponse();
                r.setNumber(0L);
                return CompletableFuture.completedFuture((T) r);
            }
            return CompletableFuture.failedFuture(new IllegalStateException("unexpected: " + request));
        }
        @Override public String getInstanceId() { return "i"; }
        @Override public String getBundleId() { return BUNDLE; }
    }
}
