package com.evento.common.messaging.consumer.impl;

import com.evento.common.messaging.consumer.ConsumerCheckpoint;
import com.evento.common.messaging.consumer.ConsumerErrorState;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.messaging.consumer.EventCheckpoint;
import com.evento.common.messaging.consumer.OptimisticLockException;
import com.evento.common.messaging.consumer.ProjectorCheckpoint;
import com.evento.common.messaging.consumer.SagaCheckpoint;
import com.evento.common.messaging.consumer.VersionedCheckpoint;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Single-JVM implementation of {@link ConsumerStateStore} backed by
 * {@link ConcurrentHashMap}s. Suitable for tests and embedded deployments
 * where durability isn't required.
 *
 * <p>Optimistic versioning is enforced via {@link ConcurrentHashMap#compute}
 * so commits serialize per-consumer without a global lock, and the stored position
 * is monotonic exactly like the JDBC store's {@code GREATEST}. The enabled flag
 * and error history live in separate maps; both treat the absence of an entry
 * as "default state" (enabled, no error).
 */
public final class InMemoryConsumerStateStore implements ConsumerStateStore {

    private final ConcurrentHashMap<String, VersionedCheckpoint> rows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> enabled = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConsumerErrorState> errors = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryConsumerStateStore() { this(Clock.systemUTC()); }
    public InMemoryConsumerStateStore(Clock clock) { this.clock = clock; }

    @Override
    public Optional<VersionedCheckpoint> read(String consumerId) {
        return Optional.ofNullable(rows.get(consumerId));
    }

    @Override
    public long commit(String consumerId, ConsumerCheckpoint checkpoint, long expectedVersion)
            throws OptimisticLockException {
        var holder = new long[]{-1L};
        var thrown = new OptimisticLockException[]{null};
        rows.compute(consumerId, (id, current) -> {
            long actual = current == null ? 0L : current.version();
            if (actual != expectedVersion) {
                thrown[0] = new OptimisticLockException(id, expectedVersion, actual);
                return current;
            }
            long next = actual + 1;
            holder[0] = next;
            // Monotonic, as the SPI requires: a lower position keeps the stored number.
            return new VersionedCheckpoint(monotonic(checkpoint, current), next);
        });
        if (thrown[0] != null) throw thrown[0];
        // A successful commit clears any recorded error.
        errors.remove(consumerId);
        return holder[0];
    }

    @Override
    public void delete(String consumerId) {
        rows.remove(consumerId);
        enabled.remove(consumerId);
        errors.remove(consumerId);
    }

    @Override
    public Stream<String> listConsumers() {
        return rows.keySet().stream();
    }

    @Override
    public boolean isEnabled(String consumerId) {
        return enabled.getOrDefault(consumerId, Boolean.TRUE);
    }

    @Override
    public void setEnabled(String consumerId, boolean enabledFlag) {
        enabled.put(consumerId, enabledFlag);
    }

    @Override
    public void setLastError(String consumerId, Throwable error) {
        Instant now = clock.instant();
        String stack = stackTraceOf(error);
        errors.merge(consumerId,
                new ConsumerErrorState(true, now, now, 1L, stack),
                (prev, fresh) -> new ConsumerErrorState(
                        true,
                        prev.errorStartAt() != null ? prev.errorStartAt() : now,
                        now,
                        prev.errorCount() + 1L,
                        stack));
    }

    @Override
    public ConsumerErrorState getErrorState(String consumerId) {
        return errors.getOrDefault(consumerId, ConsumerErrorState.healthy());
    }

    private static ConsumerCheckpoint monotonic(ConsumerCheckpoint next, VersionedCheckpoint current) {
        if (current == null) return next;
        long stored = current.checkpoint().lastSequenceNumber();
        if (next.lastSequenceNumber() >= stored) return next;
        return switch (next) {
            case EventCheckpoint ignored -> new EventCheckpoint(stored);
            case SagaCheckpoint ignored -> new SagaCheckpoint(stored);
            case ProjectorCheckpoint ignored -> new ProjectorCheckpoint(stored);
        };
    }

    private static String stackTraceOf(Throwable t) {
        if (t == null) return null;
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
