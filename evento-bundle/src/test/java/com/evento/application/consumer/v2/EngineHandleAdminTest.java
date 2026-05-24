package com.evento.application.consumer.v2;

import com.evento.application.consumer.ConsumerHandle;
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
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-engine {@link ConsumerHandle} smoke tests. Each engine's admin surface
 * (status snapshot, DLQ get/setRetry/delete, last consumed event lookup)
 * delegates directly to the v2 SPIs — these tests pin the delegation so a
 * future refactor cannot quietly drop one of the wires.
 *
 * <p>The actual consume + checkpoint behaviour is exercised by
 * {@code ConsumerProcessorTest} in evento-common; this file only verifies
 * the engine-level pass-through.
 */
class EngineHandleAdminTest {

    private InMemoryConsumerStateStore stateStore;
    private InMemoryDeadEventQueue dlq;
    private ConsumerProcessor processor;
    private RecordingServer server;

    @BeforeEach
    void setUp() {
        stateStore = new InMemoryConsumerStateStore();
        dlq = new InMemoryDeadEventQueue();
        server = new RecordingServer();
        processor = ConsumerProcessor.builder()
                .eventoServer(server)
                .lock(new InMemoryConsumerLock())
                .stateStore(stateStore)
                .sagaStateStore(new InMemorySagaStateStore())
                .deadEventQueue(dlq)
                .dedupeStore(new InMemoryDedupeStore())
                .observerExecutor(Runnable::run)
                .timeoutMillis(5_000)
                .build();
    }

    @Test
    void projectorHandleSurfaceDelegates() throws Exception {
        ConsumerHandle h = newProjector();
        assertHandleSurface(h);
    }

    @Test
    void sagaHandleSurfaceDelegates() throws Exception {
        ConsumerHandle h = newSaga();
        assertHandleSurface(h);
    }

    @Test
    void observerHandleSurfaceDelegates() throws Exception {
        ConsumerHandle h = newObserver();
        assertHandleSurface(h);
    }

    // --- shared assertions -------------------------------------------------

    private void assertHandleSurface(ConsumerHandle h) throws Exception {
        // 1. Last consumed event hits the server (no checkpoint yet) → seeded.
        server.nextHead(42L);
        long head = h.getLastConsumedEvent();
        assertThat(head).isEqualTo(42L);

        // 2. Status reflects an enabled consumer with no in-error state by default.
        var status = h.toConsumerStatus();
        assertThat(status.isEnabled()).isTrue();
        assertThat(status.isInError()).isFalse();

        // 3. DLQ ops round-trip through the SPI.
        var failed = pubEvent(7L);
        dlq.add(h.getConsumerId(), failed, new RuntimeException("boom"));
        assertThat(h.getDeadEventQueue()).hasSize(1);

        h.setDeadEventRetry(7L, true);
        assertThat(dlq.getRetriable(h.getConsumerId())).hasSize(1);

        h.deleteDeadEvent(7L);
        assertThat(h.getDeadEventQueue()).isEmpty();
    }

    // --- engine constructors ----------------------------------------------

    private static DispatchContext newDispatchContext() {
        return new DispatchContext(
                new TracingAgent("b", 1),
                (c, m) -> null,
                new com.evento.application.manager.LogTracesMessageHandlerInterceptor());
    }

    private ProjectorEngine newProjector() {
        return new ProjectorEngine(
                "b", "Proj", 1, "ctx",
                () -> false, processor, stateStore, dlq,
                new HashMap<>(), newDispatchContext(),
                10, 100, new AtomicInteger(1), () -> {});
    }

    private SagaEngine newSaga() {
        return new SagaEngine(
                "b", "Saga", 1, "ctx",
                () -> false, processor, stateStore, dlq,
                new HashMap<>(), newDispatchContext(),
                10, 100);
    }

    private ObserverEngine newObserver() {
        return new ObserverEngine(
                "b", "Obs", 1, "ctx",
                () -> false, processor, stateStore, dlq,
                new HashMap<>(), newDispatchContext(),
                10, 100);
    }

    private static PublishedEvent pubEvent(long seq) {
        var pe = new PublishedEvent();
        pe.setEventSequenceNumber(seq);
        pe.setEventName("TestEvent");
        pe.setAggregateId("agg-" + seq);
        // EventMessage needs a concrete subclass so InMemoryDeadEventQueue.getAll
        // can read .getContext() when building the dashboard snapshot.
        var payload = new com.evento.common.modeling.messaging.payload.DomainEvent() {};
        var em = new com.evento.common.modeling.messaging.message.application.DomainEventMessage(payload);
        em.setContext("ctx");
        pe.setEventMessage(em);
        return pe;
    }

    /** Minimal EventoServer that records the head requested and serves canned values. */
    static final class RecordingServer implements EventoServer {
        private Long nextHead;
        void nextHead(long h) { this.nextHead = h; }
        @Override public void send(Serializable message) {}
        @SuppressWarnings("unchecked")
        @Override
        public <T extends Serializable> CompletableFuture<T> request(Serializable request, long timeout, TimeUnit unit) {
            if (request instanceof EventLastSequenceNumberRequest) {
                long h = nextHead != null ? nextHead : 0L;
                nextHead = null;
                var r = new EventLastSequenceNumberResponse();
                r.setNumber(h);
                return CompletableFuture.completedFuture((T) r);
            }
            if (request instanceof EventFetchRequest) {
                return CompletableFuture.completedFuture((T) new EventFetchResponse(new java.util.ArrayList<>()));
            }
            return CompletableFuture.failedFuture(new IllegalStateException("unexpected: " + request));
        }
        @Override public String getInstanceId() { return "i"; }
        @Override public String getBundleId() { return "b"; }
    }
}
