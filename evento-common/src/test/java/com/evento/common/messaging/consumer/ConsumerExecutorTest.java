package com.evento.common.messaging.consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@link ConsumerExecutor} contract the consume loop depends on:
 * bounded concurrency, "started" (not "enqueued", not "finished") as the completion signal,
 * and an empty result meaning the task will never run.
 */
@Timeout(30)
class ConsumerExecutorTest {

    @Test
    void neverRunsMoreTasksThanItsCapacity() throws Exception {
        try (var executor = ConsumerExecutors.virtual("test", 3)) {
            var concurrent = new AtomicInteger();
            var peak = new AtomicInteger();
            var release = new CountDownLatch(1);
            var allStarted = new CountDownLatch(3);

            for (int i = 0; i < 20; i++) {
                executor.submit(() -> {
                    int now = concurrent.incrementAndGet();
                    peak.updateAndGet(p -> Math.max(p, now));
                    allStarted.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        concurrent.decrementAndGet();
                    }
                }, Duration.ofMillis(50));
            }

            assertThat(allStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(peak.get()).isEqualTo(3);
            release.countDown();
            assertThat(executor.awaitQuiescence(Duration.ofSeconds(5))).isTrue();
        }
    }

    @Test
    void returnedFutureCompletesWhenTheTaskStartsNotWhenItFinishes() throws Exception {
        try (var executor = ConsumerExecutors.virtual("test", 1)) {
            var release = new CountDownLatch(1);
            var finished = new AtomicBoolean(false);

            var started = executor.submit(() -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                finished.set(true);
            }, Duration.ofSeconds(1)).orElseThrow();

            // Completes while the body is still blocked — this is the checkpoint trigger.
            started.get(5, TimeUnit.SECONDS);
            assertThat(finished).isFalse();
            assertThat(executor.inFlight()).isEqualTo(1);

            release.countDown();
            assertThat(executor.awaitQuiescence(Duration.ofSeconds(5))).isTrue();
            assertThat(finished).isTrue();
        }
    }

    @Test
    void saturatedSubmitReturnsEmptyAndTheTaskNeverRuns() throws Exception {
        try (var executor = ConsumerExecutors.virtual("test", 1)) {
            var release = new CountDownLatch(1);
            var occupied = new CountDownLatch(1);
            executor.submit(() -> {
                occupied.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, Duration.ofSeconds(1)).orElseThrow();
            assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();

            var ranAnyway = new AtomicBoolean(false);
            var rejected = executor.submit(() -> ranAnyway.set(true), Duration.ofMillis(100));

            assertThat(rejected).isEmpty();
            release.countDown();
            assertThat(executor.awaitQuiescence(Duration.ofSeconds(5))).isTrue();
            // The contract the consume loop relies on to decide not to checkpoint.
            assertThat(ranAnyway).isFalse();
        }
    }

    @Test
    void awaitQuiescenceReportsFalseWhenWorkOutlivesTheDeadline() throws Exception {
        try (var executor = ConsumerExecutors.virtual("test", 1)) {
            var release = new CountDownLatch(1);
            executor.submit(() -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, Duration.ofSeconds(1)).orElseThrow();

            assertThat(executor.awaitQuiescence(Duration.ofMillis(200))).isFalse();
            release.countDown();
            assertThat(executor.awaitQuiescence(Duration.ofSeconds(5))).isTrue();
        }
    }

    @Test
    void shutdownStopsAcceptingWork() throws Exception {
        var executor = ConsumerExecutors.pooled("test", 2);
        executor.submit(() -> { }, Duration.ofSeconds(1)).orElseThrow();
        executor.shutdown(Duration.ofSeconds(5));

        assertThat(executor.submit(() -> { }, Duration.ofSeconds(1))).isEmpty();
        // Idempotent.
        executor.shutdown(Duration.ofSeconds(1));
    }

    @Test
    void pooledExecutorHonoursItsThreadCount() throws Exception {
        try (var executor = ConsumerExecutors.pooled("test", 2)) {
            var concurrent = new AtomicInteger();
            var peak = new AtomicInteger();
            var release = new CountDownLatch(1);
            var started = new CountDownLatch(2);

            for (int i = 0; i < 6; i++) {
                executor.submit(() -> {
                    // updateAndGet may retry its function, so the increment must happen
                    // exactly once outside it.
                    int now = concurrent.incrementAndGet();
                    peak.updateAndGet(p -> Math.max(p, now));
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        concurrent.decrementAndGet();
                    }
                }, Duration.ofMillis(50));
            }

            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(peak.get()).isEqualTo(2);
            release.countDown();
            assertThat(executor.awaitQuiescence(Duration.ofSeconds(5))).isTrue();
        }
    }

    @Test
    void unboundedAdapterPreservesLegacyFanOut() throws Exception {
        var delegate = Executors.newVirtualThreadPerTaskExecutor();
        try (var executor = ConsumerExecutors.unbounded("legacy", delegate)) {
            assertThat(executor.capacity()).isEqualTo(Integer.MAX_VALUE);

            var release = new CountDownLatch(1);
            var started = new CountDownLatch(10);
            for (int i = 0; i < 10; i++) {
                assertThat(executor.submit(() -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, Duration.ofMillis(1))).isPresent();
            }
            // No capacity limit: all ten run at once even with a 1ms admission budget.
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(executor.awaitQuiescence(Duration.ofSeconds(5))).isTrue();
        } finally {
            delegate.shutdownNow();
        }
    }
}
