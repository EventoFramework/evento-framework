package com.evento.common.messaging.consumer.v2;

import com.evento.common.messaging.bus.EventoServer;
import com.evento.common.messaging.consumer.ConsumerDisabledException;
import com.evento.common.messaging.consumer.EventFetchRequest;
import com.evento.common.messaging.consumer.EventFetchResponse;
import com.evento.common.messaging.consumer.EventLastSequenceNumberRequest;
import com.evento.common.messaging.consumer.EventLastSequenceNumberResponse;
import com.evento.common.messaging.consumer.v2.impl.InMemoryConsumerLock;
import com.evento.common.messaging.consumer.v2.impl.InMemoryConsumerStateStore;
import com.evento.common.messaging.consumer.v2.impl.InMemoryDeadEventQueue;
import com.evento.common.messaging.consumer.v2.impl.InMemoryDedupeStore;
import com.evento.common.messaging.consumer.v2.impl.InMemorySagaStateStore;
import com.evento.common.modeling.state.SagaState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsumerProcessorTest {

    private FakeEventoServer server;
    private InMemoryConsumerLock lock;
    private InMemoryConsumerStateStore stateStore;
    private InMemorySagaStateStore sagaStateStore;
    private InMemoryDeadEventQueue dlq;
    private InMemoryDedupeStore dedupe;
    private Executor directExecutor;
    private ConsumerProcessor processor;

    @BeforeEach
    void setUp() {
        server = new FakeEventoServer();
        lock = new InMemoryConsumerLock();
        stateStore = new InMemoryConsumerStateStore();
        sagaStateStore = new InMemorySagaStateStore();
        dlq = new InMemoryDeadEventQueue();
        dedupe = new InMemoryDedupeStore();
        directExecutor = Runnable::run;
        processor = ConsumerProcessor.builder()
                .eventoServer(server)
                .lock(lock)
                .stateStore(stateStore)
                .sagaStateStore(sagaStateStore)
                .deadEventQueue(dlq)
                .dedupeStore(dedupe)
                .observerExecutor(directExecutor)
                .timeoutMillis(5_000)
                .build();
    }

    // --- Projector ---------------------------------------------------------

    @Test
    void projectorConsumesBatchAndAdvancesCheckpoint() throws Throwable {
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"), TestEvents.event(3, "E3"));
        List<Long> seen = new ArrayList<>();

        int consumed = processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> seen.add(e.getEventSequenceNumber()), 10);

        assertThat(consumed).isEqualTo(3);
        assertThat(seen).containsExactly(1L, 2L, 3L);
        assertThat(stateStore.read("c1").orElseThrow().checkpoint())
                .isEqualTo(new ProjectorCheckpoint(3L));
    }

    @Test
    void projectorReturnsMinusOneWhenLockHeldByAnotherCaller() throws Throwable {
        var held = lock.tryAcquire("c1").orElseThrow();
        try {
            int consumed = processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> {}, 10);
            assertThat(consumed).isEqualTo(-1);
        } finally {
            held.close();
        }
    }

    @Test
    void projectorHandlerExceptionMovesEventToDlqAndStillAdvances() throws Throwable {
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));
        int consumed = processor.consumeEventsForProjector("c1", "ProjA", "ctx",
                e -> { throw new RuntimeException("boom @ " + e.getEventSequenceNumber()); }, 10);

        assertThat(consumed).isEqualTo(2);
        assertThat(dlq.getAll("c1")).hasSize(2);
        assertThat(stateStore.read("c1").orElseThrow().checkpoint())
                .isEqualTo(new ProjectorCheckpoint(2L));
    }

    @Test
    void projectorConsumerDisabledExceptionShortCircuits() throws Throwable {
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"), TestEvents.event(3, "E3"));
        AtomicInteger seen = new AtomicInteger();
        int consumed = processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> {
            seen.incrementAndGet();
            throw new ConsumerDisabledException();
        }, 10);

        assertThat(seen.get()).isEqualTo(1);
        assertThat(consumed).isEqualTo(0); // returns BEFORE the first advance, matches v1
        assertThat(stateStore.read("c1")).isEmpty();
    }

    @Test
    void projectorResumesFromPersistedCheckpoint() throws Throwable {
        stateStore.commit("c1", new ProjectorCheckpoint(42L), 0L);
        server.nextFetch(); // empty
        processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> {}, 10);
        assertThat(server.lastFetch).isNotNull();
        assertThat(server.lastFetch.getLastSequenceNumber()).isEqualTo(42L);
    }

    @Test
    void projectorStartsFromZeroOnFirstRun() throws Throwable {
        server.nextFetch();
        processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> {}, 10);
        assertThat(server.lastFetch.getLastSequenceNumber()).isEqualTo(0L);
    }

    // --- Observer ---------------------------------------------------------

    @Test
    void observerSeedsFromHeadOnFirstRun() throws Throwable {
        server.nextHead(100L);
        server.nextFetch();
        processor.consumeEventsForObserver("c1", "ObsA", "ctx", e -> {}, 10);
        assertThat(server.lastFetch.getLastSequenceNumber()).isEqualTo(100L);
        assertThat(stateStore.read("c1").orElseThrow().checkpoint())
                .isEqualTo(new EventCheckpoint(100L));
    }

    @Test
    void observerRunsHandlerOnExecutorAndDedupesEvents() throws Throwable {
        server.nextHead(0L);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));
        List<Long> seen = new ArrayList<>();
        processor.consumeEventsForObserver("c1", "ObsA", "ctx", e -> seen.add(e.getEventSequenceNumber()), 10);

        // Second occurrence of seq=1 is dedup'd; processor still advances the checkpoint past it.
        assertThat(seen).containsExactly(1L, 2L);
        assertThat(stateStore.read("c1").orElseThrow().checkpoint())
                .isEqualTo(new EventCheckpoint(2L));
    }

    // --- Saga --------------------------------------------------------------

    @Test
    void sagaConsumeCreatesInstanceWhenLookupReturnsNothing() throws Throwable {
        server.nextHead(0L);
        server.nextFetch(TestEvents.event(1, "OrderPlaced"));

        AtomicReference<SagaState> captured = new AtomicReference<>();
        processor.consumeEventsForSaga("c1", "OrderSaga", "ctx", (fetcher, event) -> {
            var old = fetcher.getLastState("OrderSaga", "orderId", "ord-1");
            assertThat(old).isNull(); // no existing instance
            var fresh = new TestSaga();
            fresh.setAssociation("orderId", "ord-1");
            captured.set(fresh);
            return fresh;
        }, 10);

        // The fresh instance is persisted under a new id.
        var found = sagaStateStore.findByAssociation("OrderSaga", "orderId", "ord-1").orElseThrow();
        assertThat(found.getState()).isSameAs(captured.get());
    }

    @Test
    void sagaConsumeUpdatesExistingInstanceWhenFetcherFindsOne() throws Throwable {
        // Pre-seed an instance.
        var existing = new TestSaga();
        existing.setAssociation("orderId", "ord-1");
        long sagaId = sagaStateStore.insert("OrderSaga", existing);

        server.nextHead(0L);
        server.nextFetch(TestEvents.event(1, "OrderShipped"));

        processor.consumeEventsForSaga("c1", "OrderSaga", "ctx", (fetcher, event) -> {
            var found = fetcher.getLastState("OrderSaga", "orderId", "ord-1");
            assertThat(found).isSameAs(existing);
            // Return a mutated copy
            var updated = new TestSaga();
            updated.setAssociation("orderId", "ord-1");
            updated.setAssociation("shipmentId", "ship-1");
            return updated;
        }, 10);

        var after = sagaStateStore.findByAssociation("OrderSaga", "shipmentId", "ship-1").orElseThrow();
        assertThat(after.getId()).isEqualTo(sagaId); // same row updated
    }

    @Test
    void sagaConsumeDeletesInstanceWhenEnded() throws Throwable {
        var existing = new TestSaga();
        existing.setAssociation("orderId", "ord-1");
        sagaStateStore.insert("OrderSaga", existing);

        server.nextHead(0L);
        server.nextFetch(TestEvents.event(1, "OrderCompleted"));

        processor.consumeEventsForSaga("c1", "OrderSaga", "ctx", (fetcher, event) -> {
            var found = fetcher.getLastState("OrderSaga", "orderId", "ord-1");
            assertThat(found).isSameAs(existing);
            existing.setEnded(true);
            return existing;
        }, 10);

        assertThat(sagaStateStore.findAll("OrderSaga")).isEmpty();
    }

    // --- Dead-event reprocess loop ----------------------------------------

    @Test
    void deadEventReprocessRetriableEntriesOnly() throws Throwable {
        var e1 = TestEvents.event(1, "E1");
        var e2 = TestEvents.event(2, "E2");
        dlq.add("c1", e1, new RuntimeException("boom"));
        dlq.add("c1", e2, new RuntimeException("boom"));
        dlq.setRetry("c1", 1, true);

        List<Long> seen = new ArrayList<>();
        processor.consumeDeadEventsForProjector("c1", "ProjA", ev -> seen.add(ev.getEventSequenceNumber()));

        assertThat(seen).containsExactly(1L);
        // Successful retry removed e1 from DLQ; e2 still present.
        assertThat(dlq.getAll("c1")).hasSize(1);
    }

    // --- handleLastError + toConsumerStatus -------------------------------

    @Test
    void handleLastErrorRecordsWhenEnabled() throws Exception {
        processor.handleLastError("c1", new RuntimeException("boom"));
        assertThat(stateStore.getErrorState("c1").inError()).isTrue();
    }

    @Test
    void handleLastErrorThrowsWhenDisabled() {
        stateStore.setEnabled("c1", false);
        assertThatThrownBy(() -> processor.handleLastError("c1", new RuntimeException("boom")))
                .isInstanceOf(ConsumerDisabledException.class);
    }

    @Test
    void toConsumerStatusAssemblesSnapshot() throws Exception {
        stateStore.commit("c1", new ProjectorCheckpoint(42L), 0L);
        stateStore.setEnabled("c1", false);
        // commit cleared error — re-set to populate
        stateStore.setLastError("c1", new RuntimeException("boom"));
        dlq.add("c1", TestEvents.event(100, "Dead"), new RuntimeException("dead"));

        var status = processor.toConsumerStatus("c1");
        assertThat(status.getLastEventSequenceNumber()).isEqualTo(42L);
        assertThat(status.isEnabled()).isFalse();
        assertThat(status.isInError()).isTrue();
        assertThat(status.getErrorCount()).isEqualTo(1L);
        assertThat(status.getError()).contains("boom");
        assertThat(status.getDeadEvents()).hasSize(1);
    }

    @Test
    void getLastEventSequenceNumberSagaOrHeadSeedsFromHeadWhenAbsent() throws Exception {
        server.nextHead(77L);
        long head = processor.getLastEventSequenceNumberSagaOrHead("c1");
        assertThat(head).isEqualTo(77L);
        assertThat(stateStore.read("c1").orElseThrow().checkpoint())
                .isEqualTo(new SagaCheckpoint(77L));
    }

    @Test
    void getLastEventSequenceNumberSagaOrHeadReturnsStoredWhenPresent() throws Exception {
        stateStore.commit("c1", new SagaCheckpoint(55L), 0L);
        long n = processor.getLastEventSequenceNumberSagaOrHead("c1");
        assertThat(n).isEqualTo(55L);
    }

    // --- Async observer behaviour (separate executor) ---------------------

    @Test
    void observerHandlerExceptionRoutesToDlqAsynchronously() throws Throwable {
        try (var pool = Executors.newSingleThreadExecutor()) {
            var asyncProcessor = ConsumerProcessor.builder()
                    .eventoServer(server)
                    .lock(new InMemoryConsumerLock())
                    .stateStore(new InMemoryConsumerStateStore())
                    .sagaStateStore(new InMemorySagaStateStore())
                    .deadEventQueue(dlq)
                    .observerExecutor(pool)
                    .timeoutMillis(5_000)
                    .build();
            server.nextHead(0L);
            server.nextFetch(TestEvents.event(1, "E1"));
            CountDownLatch done = new CountDownLatch(1);
            asyncProcessor.consumeEventsForObserver("c-async", "ObsA", "ctx", e -> {
                done.countDown();
                throw new RuntimeException("boom");
            }, 10);
            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
            // The DLQ insert is on the same async task; give it a moment to complete.
            pool.shutdown();
            assertThat(pool.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            assertThat(dlq.getAll("c-async")).hasSize(1);
        }
    }

    // --- helpers -----------------------------------------------------------

    static final class TestSaga extends SagaState {}

    /** Fake EventoServer that replies with programmable canned responses. */
    static final class FakeEventoServer implements EventoServer {
        private EventFetchResponse nextFetchResponse;
        private Long nextHeadResponse;
        EventFetchRequest lastFetch;

        void nextHead(long head) { this.nextHeadResponse = head; }

        void nextFetch(com.evento.common.modeling.messaging.dto.PublishedEvent... events) {
            var list = new ArrayList<com.evento.common.modeling.messaging.dto.PublishedEvent>(events.length);
            for (var e : events) list.add(e);
            this.nextFetchResponse = new EventFetchResponse(list);
        }

        @Override
        public void send(Serializable message) { throw new UnsupportedOperationException("not used in tests"); }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends Serializable> CompletableFuture<T> request(Serializable request, long timeout, TimeUnit unit) {
            if (request instanceof EventFetchRequest f) {
                lastFetch = f;
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
            return CompletableFuture.failedFuture(new IllegalStateException("unexpected request: " + request));
        }

        @Override public String getInstanceId() { return "test-instance"; }
        @Override public String getBundleId() { return "test-bundle"; }
    }
}
