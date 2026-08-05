package com.evento.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Local (JVM-level) locking semantics of {@link PgDistributedLock}, exercised in embedded
 * mode (null DataSource) so no PostgreSQL is involved. The distributed half is covered by
 * PgDistributedLockIT in evento-consumer-state-store-jdbc.
 */
class PgDistributedLockTest {

    /**
     * Regression for the release() race (issue #216): release() used to decrement the
     * wrapper's queue count and unmap it in two separate steps, so a concurrent acquire()
     * could join a wrapper that was about to be unmapped — its own release() then found no
     * mapping and threw IllegalMonitorStateException despite legitimately holding the lock.
     * All contention is funnelled through one key, mirroring EventStore's single "es-lock".
     */
    @Test
    @Timeout(60)
    void concurrentAcquireReleaseOnOneKeyNeverThrowsAndStaysMutuallyExclusive() throws Exception {
        var lock = new PgDistributedLock(null);
        final int threads = 8;
        final int iterations = 5_000;
        // Non-atomic on purpose: only mutual exclusion makes the final count correct.
        final int[] counter = {0};
        var start = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<CompletableFuture<Void>> workers = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                workers.add(CompletableFuture.runAsync(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    for (int i = 0; i < iterations; i++) {
                        lock.lockedArea("es-lock", () -> counter[0]++);
                    }
                }, pool));
            }
            start.countDown();
            // Any IllegalMonitorStateException from a legitimate release surfaces here.
            assertThatCode(() -> CompletableFuture.allOf(workers.toArray(CompletableFuture[]::new)).join())
                    .doesNotThrowAnyException();
            assertThat(counter[0]).isEqualTo(threads * iterations);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void releaseWithoutAcquireStillThrows() {
        var lock = new PgDistributedLock(null);
        assertThatThrownBy(() -> lock.release("never-acquired"))
                .isInstanceOf(IllegalMonitorStateException.class)
                .hasMessageContaining("never-acquired");
    }

    @Test
    void lockedAreaAttachesReleaseFailureAsSuppressedInsteadOfMaskingTheRealException() {
        var lock = new PgDistributedLock(null);
        // The runnable maliciously releases the lock itself, so the finally-release fails.
        Throwable thrown = catchThrowable(() -> lock.lockedArea("mask-key", () -> {
            lock.release("mask-key");
            throw new IllegalStateException("the real failure");
        }));
        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("the real failure");
        assertThat(thrown.getSuppressed())
                .anySatisfy(s -> assertThat(s).isInstanceOf(IllegalMonitorStateException.class));
    }

    @Test
    void lockedAreaDoesNotTurnASuccessfulCriticalSectionIntoAFailureWhenReleaseFails() {
        var lock = new PgDistributedLock(null);
        // Same sabotage, but the critical section succeeds: the failure is logged, not thrown.
        assertThatCode(() -> lock.lockedArea("success-key", () -> lock.release("success-key")))
                .doesNotThrowAnyException();
    }

    @Test
    void tryLockedAreaSkipsWhenHeldAndRunsWhenFree() throws Exception {
        var lock = new PgDistributedLock(null);
        var holderInside = new CountDownLatch(1);
        var holderMayExit = new CountDownLatch(1);
        var holder = CompletableFuture.runAsync(() -> lock.lockedArea("busy-key", () -> {
            holderInside.countDown();
            if (!holderMayExit.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("holder was never released");
            }
        }));
        assertThat(holderInside.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        var ran = new boolean[]{false};
        lock.tryLockedArea("busy-key", () -> ran[0] = true);
        assertThat(ran[0]).as("tryLockedArea must skip while the key is held").isFalse();

        holderMayExit.countDown();
        holder.join();

        lock.tryLockedArea("busy-key", () -> ran[0] = true);
        assertThat(ran[0]).as("tryLockedArea must run once the key is free").isTrue();
    }
}
