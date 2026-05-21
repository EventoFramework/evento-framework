package com.evento.common.messaging.consumer.v2;

import com.evento.common.messaging.consumer.v2.impl.InMemoryDedupeStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDedupeStoreTest {

    @Test
    void firstClaimWinsSecondLoses() {
        var store = new InMemoryDedupeStore();
        assertThat(store.tryClaim("c1", "e1")).isTrue();
        assertThat(store.tryClaim("c1", "e1")).isFalse();
    }

    @Test
    void differentConsumersClaimSameEventIndependently() {
        var store = new InMemoryDedupeStore();
        assertThat(store.tryClaim("c1", "e1")).isTrue();
        assertThat(store.tryClaim("c2", "e1")).isTrue();
    }

    @Test
    void releaseAllowsReclaim() {
        var store = new InMemoryDedupeStore();
        assertThat(store.tryClaim("c1", "e1")).isTrue();
        store.release("c1", "e1");
        assertThat(store.tryClaim("c1", "e1")).isTrue();
    }

    @Test
    void releaseOnUnknownKeyIsNoOp() {
        var store = new InMemoryDedupeStore();
        store.release("c1", "e1");
        assertThat(store.tryClaim("c1", "e1")).isTrue();
    }

    @Test
    void sweepBeforeRemovesOlderEntriesOnly() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        var mutableClock = new MutableClock(t0);
        var store = new InMemoryDedupeStore(mutableClock);

        store.tryClaim("c1", "old");
        mutableClock.advanceTo(t0.plusSeconds(60));
        store.tryClaim("c1", "new");

        int removed = store.sweepBefore(t0.plusSeconds(30));
        assertThat(removed).isEqualTo(1);
        assertThat(store.size()).isEqualTo(1);

        // "old" is gone — fresh claim succeeds.
        assertThat(store.tryClaim("c1", "old")).isTrue();
        // "new" still claimed.
        assertThat(store.tryClaim("c1", "new")).isFalse();
    }

    @Test
    void concurrentClaimsForSameKeyHaveExactlyOneWinner() throws Exception {
        var store = new InMemoryDedupeStore();
        int contenders = 32;
        try (ExecutorService pool = Executors.newFixedThreadPool(contenders)) {
            AtomicInteger winners = new AtomicInteger();
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                futures.add(pool.submit(() -> {
                    if (store.tryClaim("c1", "shared")) winners.incrementAndGet();
                }));
            }
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
            assertThat(winners.get()).isEqualTo(1);
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) { this.now = start; }
        void advanceTo(Instant t) { this.now = t; }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
