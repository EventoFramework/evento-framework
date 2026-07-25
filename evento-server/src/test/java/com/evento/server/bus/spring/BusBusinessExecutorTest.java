package com.evento.server.bus.spring;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BusBusinessExecutorTest {

    private static ThreadFactory factory() {
        var counter = new AtomicLong();
        return r -> {
            var t = new Thread(r, "test-bus-business-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private static BusBusinessExecutor executor(int core, int max, int queue) {
        return new BusBusinessExecutor(core, max, 1000L,
                new BusBusinessExecutor.GrowthFirstQueue(queue), factory(), 10_000L);
    }

    /**
     * The regression this class exists for: a stock ThreadPoolExecutor with a deep
     * queue never grows past core, so the configured maximum is unreachable under
     * load and requests pile up behind a fraction of the intended capacity.
     */
    @Test
    void growsToMaxBeforeQueueing() throws Exception {
        var pool = executor(2, 8, 1000);
        var release = new CountDownLatch(1);
        try {
            for (int i = 0; i < 8; i++) {
                pool.execute(() -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            // Despite a queue with room for 1000, all 8 tasks must be running on
            // 8 threads rather than 2 running and 6 waiting.
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(pool.getPoolSize()).isEqualTo(8));
            assertThat(pool.queueDepth()).isZero();
            assertThat(pool.saturatedCount()).isZero();
        } finally {
            release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void queuesOnlyOncePoolIsAtMax() throws Exception {
        var pool = executor(1, 2, 10);
        var release = new CountDownLatch(1);
        try {
            for (int i = 0; i < 5; i++) {
                pool.execute(() -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(pool.getPoolSize()).isEqualTo(2));
            // 2 running, the other 3 waiting — queueing is the fallback, not the default.
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(pool.queueDepth()).isEqualTo(3));
            assertThat(pool.submittedCount()).isEqualTo(5);
            assertThat(pool.saturatedCount()).isZero();
        } finally {
            release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** Past pool and queue, the caller runs the task and saturation is recorded. */
    @Test
    void countsSaturationWhenPoolAndQueueAreBothFull() throws Exception {
        var pool = executor(1, 1, 1);
        var release = new CountDownLatch(1);
        try {
            pool.execute(() -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(pool.getPoolSize()).isEqualTo(1));
            pool.execute(() -> { });                 // fills the single queue slot

            var ranOnCaller = new AtomicLong();
            long callerThreadId = Thread.currentThread().threadId();
            pool.execute(() -> {
                if (Thread.currentThread().threadId() == callerThreadId) {
                    ranOnCaller.incrementAndGet();
                }
            });

            assertThat(ranOnCaller.get()).isEqualTo(1);
            assertThat(pool.saturatedCount()).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void submittedCountReturnsToZeroWhenWorkCompletes() throws Exception {
        var pool = executor(2, 4, 100);
        try {
            for (int i = 0; i < 20; i++) {
                pool.execute(() -> { });
            }
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(pool.submittedCount()).isZero());
            assertThat(pool.queueDepth()).isZero();
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
