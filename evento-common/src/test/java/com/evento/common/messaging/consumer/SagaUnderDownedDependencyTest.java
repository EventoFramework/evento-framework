package com.evento.common.messaging.consumer;

import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.impl.InMemoryConsumerLock;
import com.evento.common.messaging.consumer.impl.InMemoryConsumerStateStore;
import com.evento.common.messaging.consumer.impl.InMemoryDeadEventQueue;
import com.evento.common.messaging.consumer.impl.InMemoryDedupeStore;
import com.evento.common.messaging.consumer.impl.InMemorySagaStateStore;
import com.evento.common.modeling.state.SagaState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Saga resilience when a saga step targets a temporarily-unavailable dependency.
 *
 * <p>The guarantees under test:
 * <ul>
 *   <li>a transient failure (downed collaborator / channel / connection) must NOT
 *       dead-letter the event and must NOT advance the checkpoint — the event
 *       redelivers until the dependency recovers (liveness);</li>
 *   <li>events processed <em>before</em> the failing one keep their committed
 *       checkpoints (no reprocessing of already-done work);</li>
 *   <li>a permanent failure still goes to the DLQ (poison-pill protection);</li>
 *   <li>once the dependency recovers, a single-side-effect handler applies its
 *       effect exactly once.</li>
 * </ul>
 * The last test documents the residual hazard: a handler that performs a
 * side effect <em>before</em> the failing step re-runs that effect on redelivery,
 * so saga handlers must keep side effects idempotent (one command per handler,
 * idempotent on the target aggregate).
 */
class SagaUnderDownedDependencyTest {

    private FakeServer server;
    private InMemoryConsumerStateStore stateStore;
    private InMemorySagaStateStore sagaStateStore;
    private InMemoryDeadEventQueue dlq;
    private ConsumerProcessor processor;

    @BeforeEach
    void setUp() {
        server = new FakeServer();
        stateStore = new InMemoryConsumerStateStore();
        sagaStateStore = new InMemorySagaStateStore();
        dlq = new InMemoryDeadEventQueue();
        processor = ConsumerProcessor.builder()
                .eventoServer(server)
                .lock(new InMemoryConsumerLock())
                .stateStore(stateStore)
                .sagaStateStore(sagaStateStore)
                .deadEventQueue(dlq)
                .dedupeStore(new InMemoryDedupeStore())
                .observerExecutor(Runnable::run)
                .timeoutMillis(5_000)
                .build();
    }

    static final class TestSaga extends SagaState {}

    /** A downstream collaborator that is "down" until {@link #available} is set. */
    static final class Dependency {
        volatile boolean available = false;
        final AtomicInteger applied = new AtomicInteger();

        void charge() throws Exception {
            if (!available) throw new ConnectException("Connection refused"); // transient
            applied.incrementAndGet();
        }
    }

    private long checkpointSeq(String consumerId) {
        return stateStore.read(consumerId).map(v -> v.checkpoint().lastSequenceNumber()).orElse(-1L);
    }

    @Test
    void downedDependency_isNotDeadLettered_and_redeliversUntilRecovery() throws Throwable {
        var dep = new Dependency();
        SagaEventConsumer handler = (fetcher, event) -> {
            dep.charge();
            var s = new TestSaga();
            s.setAssociation("orderId", "o1");
            return s;
        };

        // --- dependency DOWN: must rethrow (transient), not advance, not DLQ ---
        server.nextHead(0L);
        server.nextFetch(TestEvents.event(1, "OrderPlaced"));
        assertThatThrownBy(() -> processor.consumeEventsForSaga("c1", "OrderSaga", "ctx", handler, 10))
                .isInstanceOf(TransientConsumerException.class)
                .hasCauseInstanceOf(ConnectException.class);
        assertThat(checkpointSeq("c1")).isEqualTo(0L);   // seeded at head=0, NOT advanced to 1
        assertThat(dlq.getAll("c1")).isEmpty();           // not stranded in the DLQ
        assertThat(dep.applied.get()).isZero();

        // --- dependency RECOVERS: redeliver the same event, applies exactly once ---
        dep.available = true;
        server.nextFetch(TestEvents.event(1, "OrderPlaced"));
        int consumed = processor.consumeEventsForSaga("c1", "OrderSaga", "ctx", handler, 10);
        assertThat(consumed).isEqualTo(1);
        assertThat(checkpointSeq("c1")).isEqualTo(1L);
        assertThat(dep.applied.get()).isEqualTo(1);       // EXACTLY once
        assertThat(dlq.getAll("c1")).isEmpty();
    }

    @Test
    void permanentFailure_isDeadLettered_andCheckpointAdvances() throws Throwable {
        SagaEventConsumer handler = (fetcher, event) -> {
            throw new IllegalArgumentException("permanent: malformed payload"); // not transient
        };
        server.nextHead(0L);
        server.nextFetch(TestEvents.event(1, "OrderPlaced"));

        int consumed = processor.consumeEventsForSaga("c1", "OrderSaga", "ctx", handler, 10);

        assertThat(consumed).isEqualTo(1);                // not rethrown
        assertThat(dlq.getAll("c1")).hasSize(1);          // poison event parked
        assertThat(checkpointSeq("c1")).isEqualTo(1L);    // stream advances past it
    }

    @Test
    void batch_keepsProgressBeforeTransientFailure() throws Throwable {
        var dep = new Dependency();
        var processedSeqs = new ArrayList<Long>();
        SagaEventConsumer handler = (fetcher, event) -> {
            if (event.getEventSequenceNumber() == 2L) {
                dep.charge();   // event 2 hits the downed dependency
            }
            processedSeqs.add(event.getEventSequenceNumber());
            var s = new TestSaga();
            s.setAssociation("orderId", "o" + event.getEventSequenceNumber());
            return s;
        };

        server.nextHead(0L);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"), TestEvents.event(3, "E3"));
        assertThatThrownBy(() -> processor.consumeEventsForSaga("c1", "OrderSaga", "ctx", handler, 10))
                .isInstanceOf(TransientConsumerException.class)
                .hasCauseInstanceOf(ConnectException.class);

        // event 1 committed; event 2 (transient) and event 3 NOT committed
        assertThat(checkpointSeq("c1")).isEqualTo(1L);
        assertThat(processedSeqs).containsExactly(1L);
        assertThat(dlq.getAll("c1")).isEmpty();

        // recover → redeliver from seq 1: events 2 and 3 now process
        dep.available = true;
        server.nextFetch(TestEvents.event(2, "E2"), TestEvents.event(3, "E3"));
        int consumed = processor.consumeEventsForSaga("c1", "OrderSaga", "ctx", handler, 10);
        assertThat(consumed).isEqualTo(2);
        assertThat(checkpointSeq("c1")).isEqualTo(3L);
        assertThat(processedSeqs).containsExactly(1L, 2L, 3L);   // event 1 NOT reprocessed
    }

    @Test
    void hazard_sideEffectBeforeFailingStep_duplicatesOnRedelivery() throws Throwable {
        // Documents WHY a saga handler must keep side effects idempotent: the
        // pre-charge effect re-runs on redelivery (at-least-once), so a
        // non-idempotent reservation would leak/duplicate.
        var dep = new Dependency();
        var reservations = new AtomicInteger();
        SagaEventConsumer handler = (fetcher, event) -> {
            reservations.incrementAndGet();   // side effect BEFORE the failing step
            dep.charge();
            var s = new TestSaga();
            s.setAssociation("orderId", "o1");
            return s;
        };

        server.nextHead(0L);
        server.nextFetch(TestEvents.event(1, "OrderPlaced"));
        assertThatThrownBy(() -> processor.consumeEventsForSaga("c1", "OrderSaga", "ctx", handler, 10))
                .isInstanceOf(TransientConsumerException.class)
                .hasCauseInstanceOf(ConnectException.class);
        assertThat(reservations.get()).isEqualTo(1);
        assertThat(dep.applied.get()).isZero();

        dep.available = true;
        server.nextFetch(TestEvents.event(1, "OrderPlaced"));
        processor.consumeEventsForSaga("c1", "OrderSaga", "ctx", handler, 10);

        // The charge lands exactly once, but the pre-charge reservation ran twice.
        assertThat(dep.applied.get()).isEqualTo(1);
        assertThat(reservations.get()).isEqualTo(2);   // <-- at-least-once redelivery duplicates it
    }

    /** Minimal EventoServer test double: canned head + one-shot fetch responses. */
    static final class FakeServer implements EventoServer {
        private EventFetchResponse nextFetchResponse;
        private Long nextHeadResponse;

        void nextHead(long head) { this.nextHeadResponse = head; }

        void nextFetch(com.evento.common.modeling.messaging.dto.PublishedEvent... events) {
            var list = new ArrayList<com.evento.common.modeling.messaging.dto.PublishedEvent>(events.length);
            for (var e : events) list.add(e);
            this.nextFetchResponse = new EventFetchResponse(list);
        }

        @Override public void send(Serializable message) { throw new UnsupportedOperationException(); }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Serializable> CompletableFuture<T> request(Serializable request, long timeout, TimeUnit unit) {
            if (request instanceof EventFetchRequest) {
                var resp = nextFetchResponse != null ? nextFetchResponse : new EventFetchResponse(new ArrayList<>());
                nextFetchResponse = null;
                return CompletableFuture.completedFuture((T) resp);
            }
            if (request instanceof EventLastSequenceNumberRequest) {
                long head = nextHeadResponse != null ? nextHeadResponse : 0L;
                nextHeadResponse = null;
                var resp = new EventLastSequenceNumberResponse();
                resp.setNumber(head);
                return CompletableFuture.completedFuture((T) resp);
            }
            return CompletableFuture.failedFuture(new IllegalStateException("unexpected: " + request));
        }

        @Override public String getInstanceId() { return "test-instance"; }
        @Override public String getBundleId() { return "test-bundle"; }
    }
}
