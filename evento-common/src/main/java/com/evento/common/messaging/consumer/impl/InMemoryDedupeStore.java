package com.evento.common.messaging.consumer.impl;

import com.evento.common.messaging.consumer.DedupeStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-JVM, thread-safe implementation of {@link DedupeStore} backed by a
 * {@link ConcurrentHashMap} keyed on {@code (consumerId, eventId)}.
 *
 * <p>Entries record their claim timestamp via the injected {@link Clock} so
 * {@link #sweepBefore} can prune deterministically in tests.
 */
public final class InMemoryDedupeStore implements DedupeStore {

    private record Key(String consumerId, String eventId) {}

    private final ConcurrentHashMap<Key, Instant> seen = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryDedupeStore() {
        this(Clock.systemUTC());
    }

    public InMemoryDedupeStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryClaim(String consumerId, String eventId) {
        return seen.putIfAbsent(new Key(consumerId, eventId), clock.instant()) == null;
    }

    @Override
    public void release(String consumerId, String eventId) {
        seen.remove(new Key(consumerId, eventId));
    }

    @Override
    public int sweepBefore(Instant threshold) {
        int[] removed = new int[]{0};
        for (Map.Entry<Key, Instant> e : seen.entrySet()) {
            if (e.getValue().isBefore(threshold)) {
                if (seen.remove(e.getKey(), e.getValue())) {
                    removed[0]++;
                }
            }
        }
        return removed[0];
    }

    /** Test helper. */
    public int size() {
        return seen.size();
    }
}
