package com.evento.common.messaging.consumer.v2.impl;

import com.evento.common.messaging.consumer.v2.ConsumerLock;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-JVM implementation of {@link ConsumerLock}. Uses a
 * {@link ConcurrentHashMap#putIfAbsent} marker per consumerId — exactly one
 * caller wins, and the {@link LockHandle} releases on close.
 *
 * <p>Suitable for single-JVM deployments and tests. Multi-instance deployments
 * must use a JDBC-backed lock (PG advisory / MySQL {@code GET_LOCK}).
 */
public final class InMemoryConsumerLock implements ConsumerLock {

    private static final Object PRESENT = new Object();
    private final ConcurrentHashMap<String, Object> taken = new ConcurrentHashMap<>();

    @Override
    public Optional<LockHandle> tryAcquire(String consumerId) {
        if (taken.putIfAbsent(consumerId, PRESENT) != null) {
            return Optional.empty();
        }
        return Optional.of(new Handle(consumerId));
    }

    private final class Handle implements LockHandle {
        private final String consumerId;
        private boolean released;

        Handle(String consumerId) { this.consumerId = consumerId; }

        @Override public String consumerId() { return consumerId; }

        @Override
        public void close() {
            if (released) return;
            released = true;
            taken.remove(consumerId);
        }
    }
}
