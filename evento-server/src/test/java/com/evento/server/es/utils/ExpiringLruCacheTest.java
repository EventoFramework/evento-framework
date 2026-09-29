package com.evento.server.es.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The event store's aggregate caches (G19): every read honours the TTL, a cached {@code null}
 * is distinguishable from a miss, and the cache survives concurrent command threads.
 */
class ExpiringLruCacheTest {

    @Test
    void everyReadHonoursTheTtl() {
        var now = new AtomicLong(1_000);
        var cache = new ExpiringLruCache<String, String>(10, 100, TimeUnit.MILLISECONDS, now::get);
        cache.put("a", "A");

        now.set(1_100); // exactly at expiry: still live
        assertThat(cache.get("a")).isEqualTo("A");
        assertThat(cache.containsKey("a")).isTrue();
        assertThat(cache.getOrDefault("a", "dflt")).isEqualTo("A");

        now.set(1_101); // past expiry: every read path must miss
        assertThat(cache.containsKey("a")).isFalse();
        assertThat(cache.getOrDefault("a", "dflt")).isEqualTo("dflt");
        assertThat(cache.lookup("a")).isNull();
        assertThat(cache.get("a")).isNull();
        assertThat(cache.size()).as("the expired entry is evicted on read").isZero();
    }

    @Test
    void cachedNullIsDistinguishableFromAMiss() {
        var now = new AtomicLong(0);
        var cache = new ExpiringLruCache<String, String>(10, 100, TimeUnit.MILLISECONDS, now::get);
        cache.put("no-snapshot", null);

        assertThat(cache.lookup("no-snapshot")).isNotNull();
        assertThat(cache.lookup("no-snapshot").getValue()).isNull();
        assertThat(cache.containsKey("no-snapshot")).isTrue();
        assertThat(cache.lookup("never-cached")).isNull();
    }

    @Test
    void evictsTheLeastRecentlyUsedEntry() {
        var cache = new ExpiringLruCache<String, Integer>(2, 1, TimeUnit.HOURS);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a");     // a is now most recently used
        cache.put("c", 3);  // evicts b
        assertThat(cache.containsKey("a")).isTrue();
        assertThat(cache.containsKey("b")).isFalse();
        assertThat(cache.containsKey("c")).isTrue();
    }

    @Test
    @Timeout(60)
    void survivesConcurrentReadersAndWriters() throws Exception {
        // An access-ordered LinkedHashMap restructures its list even on get(): unsynchronised,
        // this workload throws or corrupts the map (lost entries, size above the bound).
        var cache = new ExpiringLruCache<Integer, Integer>(64, 1, TimeUnit.HOURS);
        int threads = 8;
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
                    var rnd = ThreadLocalRandom.current();
                    for (int i = 0; i < 200_000; i++) {
                        int key = rnd.nextInt(256);
                        switch (rnd.nextInt(4)) {
                            case 0 -> cache.put(key, key);
                            case 1 -> {
                                Integer v = cache.get(key);
                                if (v != null && v != key) throw new AssertionError("wrong value for " + key);
                            }
                            case 2 -> cache.containsKey(key);
                            default -> cache.remove(key);
                        }
                    }
                }, pool));
            }
            start.countDown();
            assertThatCode(() -> CompletableFuture.allOf(workers.toArray(CompletableFuture[]::new)).join())
                    .doesNotThrowAnyException();
            assertThat(cache.size()).isLessThanOrEqualTo(64);
        } finally {
            pool.shutdownNow();
        }
    }
}
