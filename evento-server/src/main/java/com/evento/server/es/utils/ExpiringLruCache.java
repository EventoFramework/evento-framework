package com.evento.server.es.utils;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ExpiringLruCache<A, B> extends LinkedHashMap<A, B> {
    private final int maxEntries;
    private final long ttlInMillis;
    private final Map<A, Long> expirationMap; // Map to track expiration times

    // Constructor to set the maxEntries and time-to-live (ttl)
    public ExpiringLruCache(final int maxEntries, long ttl, TimeUnit timeUnit) {
        super(maxEntries + 1, 1.0f, true);
        this.maxEntries = maxEntries;
        this.ttlInMillis = timeUnit.toMillis(ttl);
        this.expirationMap = new HashMap<>();
    }

    // Overridden method to decide when to remove the eldest entry
    @Override
    protected boolean removeEldestEntry(Map.Entry<A, B> eldest) {
        // Remove the eldest entry if the size exceeds the maximum entries
        return super.size() > maxEntries;
    }

    // Method to put a new entry in the cache
    public B put(A key, B value) {
        expirationMap.put(key, System.currentTimeMillis() + ttlInMillis);
        return super.put(key, value);
    }


    // Method to get an entry from the cache
    public B get(Object key) {
        Long expiryTime = expirationMap.get(key);
        if (expiryTime == null || System.currentTimeMillis() > expiryTime) {
            // If the entry is expired or doesn't exist, remove it and return null
            remove(key);
            expirationMap.remove(key);
            return null;
        }
        return super.get(key);
    }
}
