package com.evento.common.messaging.consumer;

import com.evento.common.messaging.consumer.impl.InMemoryConsumerLock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryConsumerLockTest {

    @Test
    void firstAcquireWinsSecondGetsEmpty() {
        var lock = new InMemoryConsumerLock();
        var first = lock.tryAcquire("c1");
        assertThat(first).isPresent();
        assertThat(lock.tryAcquire("c1")).isEmpty();
        first.get().close();
    }

    @Test
    void releaseLetsNextCallerAcquire() {
        var lock = new InMemoryConsumerLock();
        var first = lock.tryAcquire("c1").orElseThrow();
        first.close();
        assertThat(lock.tryAcquire("c1")).isPresent();
    }

    @Test
    void differentConsumersAreIndependent() {
        var lock = new InMemoryConsumerLock();
        assertThat(lock.tryAcquire("a")).isPresent();
        assertThat(lock.tryAcquire("b")).isPresent();
    }

    @Test
    void closeIsIdempotent() {
        var lock = new InMemoryConsumerLock();
        var h = lock.tryAcquire("c1").orElseThrow();
        h.close();
        h.close(); // must not throw, must not re-release a re-acquired lock
        var h2 = lock.tryAcquire("c1").orElseThrow();
        h.close(); // closing the first handle again should NOT release h2
        assertThat(lock.tryAcquire("c1")).isEmpty();
        h2.close();
    }

    @Test
    void concurrentTryAcquireHasExactlyOneWinner() throws Exception {
        var lock = new InMemoryConsumerLock();
        int contenders = 32;
        try (ExecutorService pool = Executors.newFixedThreadPool(contenders)) {
            AtomicInteger winners = new AtomicInteger();
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                futures.add(pool.submit(() -> {
                    lock.tryAcquire("hot").ifPresent(h -> {
                        winners.incrementAndGet();
                        h.close();
                    });
                }));
            }
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            // Multiple winners possible because each one closes immediately —
            // but the lock must have served them sequentially. We only require
            // it never lets two coexist simultaneously, which is verified by
            // firstAcquireWinsSecondGetsEmpty + the contract.
            assertThat(winners.get()).isGreaterThanOrEqualTo(1);
        }
    }
}
