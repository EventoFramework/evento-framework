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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CheckpointMode#WATERMARK}: the checkpoint tracks the highest contiguous
 * <em>completed</em> sequence, so a crash replays the in-flight window instead of losing it.
 */
@Timeout(30)
class WatermarkCheckpointTest {

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
        processor = newProcessor();
    }

    private ConsumerProcessor newProcessor() {
        return ConsumerProcessor.builder()
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

    private int consume(ConsumerProcessor p, EventConsumer consumer) throws Throwable {
        return p.consumeEventsForProjector("c1", "ProjA", "ctx", consumer, 10,
                always(executor), CheckpointMode.WATERMARK);
    }

    private long checkpoint() {
        return stateStore.read("c1").map(v -> v.checkpoint().lastSequenceNumber()).orElse(0L);
    }

    @Test
    void checkpointHoldsAtTheCompletedPrefixWhileLaterEventsAreStillRunning() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 4);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"),
                TestEvents.event(3, "E3"), TestEvents.event(4, "E4"));

        // Event 2 blocks; 1, 3 and 4 complete. The contiguous completed prefix is
        // therefore just {1} — 3 and 4 must NOT drag the checkpoint past the gap at 2.
        var releaseTwo = new CountDownLatch(1);
        var reachedTwo = new CountDownLatch(1);

        int consumed = consume(processor, e -> {
            if (e.getEventSequenceNumber() == 2L) {
                reachedTwo.countDown();
                releaseTwo.await();
            }
        });

        assertThat(consumed).isEqualTo(4);
        assertThat(reachedTwo.await(5, TimeUnit.SECONDS)).isTrue();
        // Let 3 and 4 finish while 2 is still held.
        Thread.sleep(200);

        consume(processor, e -> { });   // triggers a watermark commit; no new events
        assertThat(checkpoint())
                .as("the gap at seq 2 must pin the checkpoint at 1")
                .isEqualTo(1L);

        releaseTwo.countDown();
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();

        consume(processor, e -> { });
        assertThat(checkpoint())
                .as("closing the gap releases the whole run of completions")
                .isEqualTo(4L);
    }

    @Test
    void aCrashReplaysOnlyTheInFlightWindow() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 4);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"),
                TestEvents.event(3, "E3"));

        var applied = new ConcurrentLinkedQueue<Long>();
        var block = new CountDownLatch(1);

        // 1 completes; 2 and 3 are still running when the "crash" happens.
        consume(processor, e -> {
            if (e.getEventSequenceNumber() > 1L) block.await();
            applied.add(e.getEventSequenceNumber());
        });
        await(() -> applied.contains(1L));
        consume(processor, e -> { });

        assertThat(checkpoint()).isEqualTo(1L);

        // Simulate the crash: a brand-new processor, so all in-memory dispatch state is
        // gone and only the persisted checkpoint survives.
        block.countDown();
        var afterRestart = newProcessor();
        server.nextFetch(TestEvents.event(2, "E2"), TestEvents.event(3, "E3"));

        var replayed = new ConcurrentLinkedQueue<Long>();
        afterRestart.consumeEventsForProjector("c1", "ProjA", "ctx",
                e -> replayed.add(e.getEventSequenceNumber()), 10,
                always(executor), CheckpointMode.WATERMARK);
        assertThat(afterRestart.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();

        // At-least-once: the events that were mid-flight come back, rather than being lost
        // as they would be under ON_START.
        assertThat(replayed).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void theFetchCursorFollowsTheDispatchFrontierSoNothingIsProcessedTwiceWithinARun() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 4);
        var seen = new ConcurrentLinkedQueue<Long>();
        var block = new CountDownLatch(1);

        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));
        consume(processor, e -> {
            seen.add(e.getEventSequenceNumber());
            block.await();
        });

        // The checkpoint is still 0 (nothing has completed), but the next cycle must not
        // re-deliver 1 and 2 — the in-memory frontier, not the checkpoint, is the cursor.
        assertThat(checkpoint()).isZero();
        server.nextFetch(TestEvents.event(3, "E3"));
        consume(processor, e -> {
            seen.add(e.getEventSequenceNumber());
            block.await();
        });

        block.countDown();
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();
        assertThat(seen).containsExactly(1L, 2L, 3L);
    }

    @Test
    void deadLetteredEventsCountAsCompletedSoOnePoisonEventCannotPinTheWatermark() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 4);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "POISON"),
                TestEvents.event(3, "E3"));

        consume(processor, e -> {
            if (e.getEventSequenceNumber() == 2L) throw new IllegalStateException("poison");
        });
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();
        consume(processor, e -> { });

        assertThat(dlq.getAll("c1")).hasSize(1);
        assertThat(checkpoint())
                .as("a dead-lettered event is resolved, not outstanding")
                .isEqualTo(3L);
    }

    @Test
    void inlineHandlersAdvanceTheWatermarkImmediately() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 4);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));

        // Null executor for every event => fully inline, so each completes before the next.
        processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> { }, 10,
                null, CheckpointMode.WATERMARK);

        assertThat(checkpoint()).isEqualTo(2L);
    }

    @Test
    void flushWatermarkPersistsAfterADrainSoAGracefulStopDoesNotReplay() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 4);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));

        consume(processor, e -> { });
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();

        // Without the flush the batch would sit completed-but-uncommitted until a next
        // cycle that a stopping bundle never runs.
        processor.flushWatermark("c1", "ProjA", ProjectorCheckpoint::new);
        assertThat(checkpoint()).isEqualTo(2L);
    }

    @Test
    void onStartModeStillCheckpointsTheFrontierNotTheCompletions() throws Throwable {
        executor = ConsumerExecutors.virtual("async", 4);
        server.nextFetch(TestEvents.event(1, "E1"), TestEvents.event(2, "E2"));
        var block = new CountDownLatch(1);

        processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> block.await(), 10,
                always(executor), CheckpointMode.ON_START);

        // The contrast that motivates WATERMARK: nothing has finished, yet both events are
        // already checkpointed and would be lost by a crash here.
        assertThat(checkpoint()).isEqualTo(2L);
        block.countDown();
        assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(5))).isTrue();
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("condition not met in 5s");
            Thread.sleep(10);
        }
    }
}
