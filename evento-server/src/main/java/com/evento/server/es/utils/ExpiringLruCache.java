package com.evento.server.es.utils;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * A bounded, access-ordered LRU cache whose entries also expire a fixed time after they were
 * written. Thread-safe: every operation runs under the cache's monitor.
 *
 * <p>It used to extend {@code LinkedHashMap} directly. That exposed the inherited
 * {@code containsKey}/{@code getOrDefault}, which ignored the TTL (so the event store served
 * expired snapshots and stories indefinitely), and — being an access-ordered map, where even
 * {@code get} restructures the list — it corrupted under the event store's concurrent command
 * threads. It now wraps the map and exposes only TTL-aware reads.
 *
 * <p>A {@code null} value is a legal cached value; use {@link #lookup} to tell "cached null"
 * from "absent or expired".
 */
public class ExpiringLruCache<A, B> {

    private record Entry<V>(V value, long expiresAtMillis) {
    }

    private final long ttlInMillis;
    private final LongSupplier clock;
    private final LinkedHashMap<A, Entry<B>> map;

    public ExpiringLruCache(final int maxEntries, long ttl, TimeUnit timeUnit) {
        this(maxEntries, ttl, timeUnit, System::currentTimeMillis);
    }

    /** Test seam: {@code clock} supplies the current time in milliseconds. */
    ExpiringLruCache(final int maxEntries, long ttl, TimeUnit timeUnit, LongSupplier clock) {
        this.ttlInMillis = timeUnit.toMillis(ttl);
        this.clock = clock;
        this.map = new LinkedHashMap<>(maxEntries + 1, 1.0f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<A, Entry<B>> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /** Caches {@code value} (which may be {@code null}) for the configured TTL. */
    public synchronized B put(A key, B value) {
        var previous = map.put(key, new Entry<>(value, clock.getAsLong() + ttlInMillis));
        return previous == null ? null : previous.value();
    }

    /**
     * The live entry for {@code key}: {@code null} if absent or expired (an expired entry is
     * evicted), otherwise a holder whose {@link Map.Entry#getValue()} may itself be {@code null}.
     */
    public synchronized Map.Entry<A, B> lookup(A key) {
        var entry = map.get(key);
        if (entry == null) return null;
        if (clock.getAsLong() > entry.expiresAtMillis()) {
            map.remove(key);
            return null;
        }
        return new AbstractMap.SimpleImmutableEntry<>(key, entry.value());
    }

    /** The cached value, or {@code null} if absent, expired, or a cached {@code null}. */
    public synchronized B get(A key) {
        var live = lookup(key);
        return live == null ? null : live.getValue();
    }

    /** TTL-aware: {@code false} for an expired entry. */
    public synchronized boolean containsKey(A key) {
        return lookup(key) != null;
    }

    /** TTL-aware: {@code defaultValue} for an absent or expired entry. */
    public synchronized B getOrDefault(A key, B defaultValue) {
        var live = lookup(key);
        return live == null ? defaultValue : live.getValue();
    }

    public synchronized B remove(A key) {
        var previous = map.remove(key);
        return previous == null ? null : previous.value();
    }

    /** Number of stored entries, including ones that have expired but were not yet evicted. */
    public synchronized int size() {
        return map.size();
    }
}
