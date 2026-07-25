package com.evento.common.messaging.consumer;

import com.evento.common.messaging.consumer.impl.InMemoryConsumerLock;
import com.evento.common.messaging.consumer.impl.InMemoryConsumerStateStore;
import com.evento.common.messaging.consumer.impl.InMemoryDeadEventQueue;
import com.evento.common.messaging.consumer.impl.InMemoryDedupeStore;
import com.evento.common.messaging.consumer.impl.InMemorySagaStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-key ordering with cross-key parallelism — the property that makes parallel consumption
 * usable by handlers that are per-aggregate sequential rather than idempotent.
 */
@Timeout(30)
class PartitionedConsumerExecutorTest {

    @Test
    void tasksSharingAKeyRunOneAtATime() throws Exception {
        try (var executor = ConsumerExecutors.partitioned("test", 8)) {
            var concurrentOnKey = new AtomicInteger();
            var sawOverlap = new AtomicBoolean(false);
            var done = new CountDownLatch(20);

            for (int i = 0; i < 20; i++) {
                executor.submit(() -> {
                    if (concurrentOnKey.incrementAndGet() > 1) sawOverlap.set(true);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        concurrentOnKey.decrementAndGet();
                        done.countDown();
                    }
                }, "same-aggregate", Duration.ofSeconds(5));
            }

            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(sawOverlap).as("one lane must never run two tasks at once").isFalse();
        }
    }

    @Test
    void differentKeysStillRunInParallel() throws Exception {
        try (var executor = ConsumerExecutors.partitioned("test", 8)) {
            var release = new CountDownLatch(1);
            var started = new CountDownLatch(4);
            var live = new AtomicInteger();
            var peak = new AtomicInteger();

            // Four keys chosen to be distinct; with 8 lanes they are very unlikely to
            // collide, and the assertion below tolerates collisions by checking >= 2.
            for (var key : List.of("agg-a", "agg-b", "agg-c", "agg-d")) {
                executor.submit(() -> {
                    int now = live.incrementAndGet();
                    peak.updateAndGet(p -> Math.max(p, now));
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        live.decrementAndGet();
                    }
                }, key, Duration.ofSeconds(5));
            }

            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(peak.get()).isGreaterThanOrEqualTo(2);
            release.countDown();
            assertThat(executor.awaitQuiescence(Duration.ofSeconds(5))).isTrue();
        }
    }

    @Test
    void aBusyLaneRefusesAdmissionRatherThanQueueing() throws Exception {
        try (var executor = ConsumerExecutors.partitioned("test", 4)) {
            var release = new CountDownLatch(1);
            var occupied = new CountDownLatch(1);

            executor.submit(() -> {
                occupied.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "hot", Duration.ofSeconds(5)).orElseThrow();
            assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();

            var ranAnyway = new AtomicBoolean(false);
            var refused = executor.submit(() -> ranAnyway.set(true), "hot", Duration.ofMillis(100));

            // Same contract as the bounded executor: empty means it will never run, so the
            // consume loop knows not to checkpoint it.
            assertThat(refused).isEmpty();
            release.countDown();
            assertThat(executor.awaitQuiescence(Duration.ofSeconds(5))).isTrue();
            assertThat(ranAnyway).isFalse();
        }
    }

    @Test
    void keylessEventsSpreadAcrossLanesInsteadOfSerialising() throws Exception {
        try (var executor = ConsumerExecutors.partitioned("test", 4)) {
            var release = new CountDownLatch(1);
            var started = new CountDownLatch(4);

            for (int i = 0; i < 4; i++) {
                assertThat(executor.submit(() -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, null, Duration.ofSeconds(2))).isPresent();
            }

            // All four run at once: with no aggregate there is no ordering to preserve.
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(executor.awaitQuiescence(Duration.ofSeconds(5))).isTrue();
        }
    }

    @Test
    void consumeLoopAppliesSameAggregateEventsInSequenceOrder() throws Throwable {
        var server = new ConsumerProcessorTest.FakeEventoServer();
        var processor = ConsumerProcessor.builder()
                .eventoServer(server)
                .lock(new InMemoryConsumerLock())
                .stateStore(new InMemoryConsumerStateStore())
                .sagaStateStore(new InMemorySagaStateStore())
                .deadEventQueue(new InMemoryDeadEventQueue())
                .dedupeStore(new InMemoryDedupeStore())
                .observerExecutor(ConsumerExecutors.virtual("observer", 4))
                .timeoutMillis(5_000)
                .submitTimeout(Duration.ofSeconds(5))
                .build();

        try (var executor = ConsumerExecutors.partitioned("ordered", 8)) {
            // Two aggregates interleaved in the stream. Each must be applied in its own
            // sequence order; the two may interleave with each other freely.
            server.nextFetch(
                    TestEvents.event(1, "E", "agg-1", "ctx"),
                    TestEvents.event(2, "E", "agg-2", "ctx"),
                    TestEvents.event(3, "E", "agg-1", "ctx"),
                    TestEvents.event(4, "E", "agg-2", "ctx"),
                    TestEvents.event(5, "E", "agg-1", "ctx"),
                    TestEvents.event(6, "E", "agg-2", "ctx"));

            Map<String, ConcurrentLinkedQueue<Long>> perAggregate = new ConcurrentHashMap<>();
            processor.consumeEventsForProjector("c1", "ProjA", "ctx", e -> {
                // Sleep varies by sequence so an unordered executor would reorder these.
                Thread.sleep(e.getEventSequenceNumber() % 2 == 1 ? 60 : 10);
                perAggregate.computeIfAbsent(e.getAggregateId(), k -> new ConcurrentLinkedQueue<>())
                        .add(e.getEventSequenceNumber());
            }, 10, event -> executor);

            assertThat(processor.awaitConsumerQuiescence("c1", Duration.ofSeconds(10))).isTrue();

            assertThat(perAggregate.get("agg-1")).containsExactly(1L, 3L, 5L);
            assertThat(perAggregate.get("agg-2")).containsExactly(2L, 4L, 6L);
        }
    }
}
