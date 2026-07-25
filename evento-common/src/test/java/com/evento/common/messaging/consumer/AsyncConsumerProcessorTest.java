package com.evento.common.messaging.consumer;

import com.evento.common.messaging.consumer.impl.InMemoryConsumerLock;
import com.evento.common.messaging.consumer.impl.InMemoryConsumerStateStore;
import com.evento.common.messaging.consumer.impl.InMemoryDeadEventQueue;
import com.evento.common.messaging.consumer.impl.InMemoryDedupeStore;
import com.evento.common.messaging.consumer.impl.InMemorySagaStateStore;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The async consume-loop semantics: the checkpoint advances when a task <em>starts</em>,
 * a saturated executor ends the cycle rather than blocking on the consumer lock, and
 * failures dead-letter because redelivery is no longer available once the checkpoint moved.
 */
@Timeout(30)
class AsyncConsumerProcessorTest {

    private ConsumerProcessorTest.FakeEventoServer server;
    private InMemoryConsumerStateStore stateStore;
    private InMemoryDeadEventQueue dlq;
    private ConsumerProcessor processor;
    private ConsumerExecutor executor;

    @BeforeEach
    void setUp() {
        server = new ConsumerProcessorTest.FakeEventoServer();
        stateStore = new InMemoryConsumerStateStore();
        dlq = new InMemoryDeadEventQueue();
        processor = ConsumerProcessor.builder()
                .eventoServer(server)
                .lock(new InMemoryConsumerLock())
                .stateStore(stateStore)
                .sagaStateStore(new InMemorySagaStateStore())
                .deadEventQueue(dlq)
                .dedupeStore(new InMemoryDedupeStore())
                .observerExecutor(ConsumerExecutors.virtual("observer", 4))
                .timeoutMillis(5_000)
                .submitTimeout(Duration.ofMillis(200))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (executor != null) executor.shutdown(Duration.ofSeconds(5));
    }

    private Function<PublishedEvent, ConsumerExecutor> always(ConsumerExecutor e) {
        return event -> e;
    }

    @Test
    void checkpointAdvancesWhenTheHandlerStartsNotWhenItFinishes() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 3);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"), TestEvents.event(3, "E3"));

        var release = new CountDownLatch(1);
        var completed = new AtomicInteger();

        int consumed = processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> {
            release.await();
            completed.incrementAndGet();
        }, 10, always(executor));

        // All three are checkpointed while none has finished — that is the whole point.
        assertThat(consumed).isEqualTo(3);
        assertThat(stateStore.read("c1").orElseThrow().checkpoint()).isEqualTo(new ProjectorCheckpoint(3L));
        assertThat(completed.get()).isZero();
        assertThat(processor.inFlightCount("c1")).isEqualTo(3);

        release.countDown();
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();
        assertThat(completed.get()).isEqualTo(3);
    }

    @Test
    void saturatedExecutorEndsTheCycleAndTheNextCycleResumesWithoutGapOrDuplicate() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 1);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"), TestEvents.event(3, "E3"));

        var release = new CountDownLatch(1);
        var seen = new ConcurrentLinkedQueue<Long>();
        EventConsumer consumer = e -> {
            seen.add(e.getEventSequenceNumber());
            release.await();
        };

        // Capacity 1: event 1 starts, event 2 cannot be admitted within submitTimeout.
        int consumed = processor.consumeEventsForProjector("c1", "ProjA", "ctx", consumer, 10, always(executor));

        assertThat(consumed).isEqualTo(1);
        assertThat(stateStore.read("c1").orElseThrow().checkpoint()).isEqualTo(new ProjectorCheckpoint(1L));

        release.countDown();
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();

        // Next cycle picks up exactly where the last one stopped.
        server.nextFetch(TestEvents.event(2, "E2"), TestEvents.event(3, "E3"));
        int consumedAgain = processor.consumeEventsForProjector("c1", "ProjA", "ctx", consumer, 10, always(executor));

        assertThat(consumedAgain).isEqualTo(2);
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();
        assertThat(stateStore.read("c1").orElseThrow().checkpoint()).isEqualTo(new ProjectorCheckpoint(3L));
        assertThat(seen).containsExactly(1L, 2L, 3L);
    }

    @Test
    void asyncHandlerFailureDeadLetters() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 2);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));

        int consumed = processor.consumeEventsForProjector("c1", "ProjA", "ctx",
                e -> { throw new IllegalStateException("boom @ " + e.getEventSequenceNumber()); },
                10, always(executor));

        assertThat(consumed).isEqualTo(2);
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();
        assertThat(dlq.getAll("c1")).hasSize(2);
        assertThat(stateStore.read("c1").orElseThrow().checkpoint()).isEqualTo(new ProjectorCheckpoint(2L));
    }

    @Test
    void consumerDisabledMidFlightDropsWithoutDeadLettering() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 2);
        server.nextFetch(TestEvents.event(1, "E1"));

        processor.consumeEventsForProjector("c1", "ProjA", "ctx",
                e -> { throw new ConsumerDisabledException(); }, 10, always(executor));

        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();
        // An operator pausing the consumer is not a consumer failure.
        assertThat(dlq.getAll("c1")).isEmpty();
    }

    @Test
    void inlineHandlerActsAsABarrierOverEarlierAsyncEvents() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 4);
        server.nextFetch(TestEvents.event(1, "ASYNC"), TestEvents.event(2, "INLINE"));

        var order = new ConcurrentLinkedQueue<String>();
        // Only the first event routes to the executor; the second runs inline.
        Function<PublishedEvent, ConsumerExecutor> resolver =
                event -> "ASYNC".equals(event.getEventName()) ? executor : null;

        processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> {
            if ("ASYNC".equals(e.getEventName())) {
                Thread.sleep(300);
                order.add("async");
            } else {
                order.add("inline");
            }
        }, 10, resolver);

        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();
        // Without the barrier the inline handler would land first and could observe state
        // from before the async event was applied.
        assertThat(order).containsExactly("async", "inline");
    }

    @Test
    void nullResolverKeepsEveryEventInline() throws Throwable {
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));
        var threads = new ConcurrentLinkedQueue<String>();
        var callerThread = Thread.currentThread().getName();

        int consumed = processor.consumeEventsForProjector("c1", "ProjA", "ctx",
                e -> threads.add(Thread.currentThread().getName()), 10, null);

        assertThat(consumed).isEqualTo(2);
        assertThat(threads).containsExactly(callerThread, callerThread);
        assertThat(processor.inFlightCount("c1")).isZero();
    }

    @Test
    void observerDispatchesThroughTheResolvedExecutor() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 2);
        server.nextHead(0);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));

        var seen = new ConcurrentLinkedQueue<Long>();
        int consumed = processor.consumeEventsForObserver("o1", "ObsA", "ctx",
                e -> seen.add(e.getEventSequenceNumber()), 10, always(executor));

        assertThat(consumed).isEqualTo(2);
        assertThat(processor.awaitConsumerQuiescence("o1", Duration.ofSeconds(5))).isTrue();
        assertThat(seen).containsExactlyInAnyOrderElementsOf(List.of(1L, 2L));
    }

    @Test
    void quiescenceOfOneConsumerIgnoresAnotherConsumersWork() throws Throwable {
        // Two consumers share one executor. Draining "c1" must not wait on "c2".
        executor = ConsumerExecutors.virtual("shared", 4);
        var block = new CountDownLatch(1);

        server.nextFetch(TestEvents.event(1, "E1"));
        processor.consumeEventsForProjector("c2", "ProjB", "ctx", e -> block.await(), 10, always(executor));

        server.nextFetch(TestEvents.event(1, "E1"));
        processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> { }, 10, always(executor));

        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();
        assertThat(processor.inFlightCount("c2")).isEqualTo(1);

        block.countDown();
        assertThat(processor.awaitConsumerQuiescence("c2", Duration.ofSeconds(5))).isTrue();
    }

    // --- Degraded backoff signal (Phase 2) ----------------------------------

    @Test
    void transientAsyncFailuresRaiseTheDegradedStreakAndACleanRunClearsIt() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 1);

        // java.net.ConnectException is on ConsumerProcessor's transient list.
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));
        processor.consumeEventsForProjector("c1", "ProjA", "ctx",
                e -> { throw new java.net.ConnectException("collaborator down"); },
                10, always(executor));
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();

        assertThat(processor.asyncTransientFailureStreak("c1"))
                .as("engines read this to back the fetch loop off")
                .isEqualTo(2);
        // Redelivery is impossible once the checkpoint moved, so they still dead-letter.
        assertThat(dlq.getAll("c1")).hasSize(2);

        // The dependency recovers: the first clean completion clears the streak.
        server.nextFetch(TestEvents.event(3, "E3"));
        processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> { }, 10, always(executor));
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();

        assertThat(processor.asyncTransientFailureStreak("c1")).isZero();
    }

    @Test
    void permanentAsyncFailuresDoNotRaiseTheDegradedStreak() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 2);
        server.nextFetch(TestEvents.event(1, "E1"));

        processor.consumeEventsForProjector("c1", "ProjA", "ctx",
                e -> { throw new IllegalArgumentException("poison event"); }, 10, always(executor));
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();

        // A poison event is not a reason to slow the whole stream down.
        assertThat(processor.asyncTransientFailureStreak("c1")).isZero();
        assertThat(dlq.getAll("c1")).hasSize(1);
    }

    // --- Status counters (Phase 2) ------------------------------------------

    @Test
    void statusReportsInFlightSubmitTimeoutsAndTransientFailures() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 1);
        var release = new CountDownLatch(1);

        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));
        processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> release.await(),
                10, always(executor));

        var busy = processor.toConsumerStatus("c1");
        assertThat(busy.getAsyncInFlight()).isEqualTo(1);
        assertThat(busy.getAsyncSubmitTimeouts())
                .as("event 2 could not be admitted within the submit timeout")
                .isEqualTo(1);

        release.countDown();
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();

        server.nextFetch(TestEvents.event(2, "E2"));
        processor.consumeEventsForProjector("c1", "ProjA", "ctx",
                e -> { throw new java.net.ConnectException("down"); }, 10, always(executor));
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();

        var idle = processor.toConsumerStatus("c1");
        assertThat(idle.getAsyncInFlight()).isZero();
        assertThat(idle.getAsyncTransientFailures()).isEqualTo(1);
    }

    @Test
    void executorStatsCountAdmissionsAndRejections() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 1);
        var release = new CountDownLatch(1);

        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));
        processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> release.await(),
                10, always(executor));

        var stats = executor.stats();
        assertThat(stats.name()).isEqualTo("async");
        assertThat(stats.capacity()).isEqualTo(1);
        assertThat(stats.admitted()).isEqualTo(1);
        assertThat(stats.rejected())
                .as("the backpressure signal operators should alert on")
                .isEqualTo(1);

        release.countDown();
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();
        assertThat(executor.stats().completed()).isEqualTo(1);
        assertThat(executor.stats().failed()).isZero();
    }

    @Test
    void unknownConsumerIsTriviallyQuiescent() throws Exception {
        assertThat(processor.awaitConsumerQuiescence("never-ran", Duration.ofMillis(1))).isTrue();
        assertThat(processor.inFlightCount("never-ran")).isZero();
    }
}
