package com.evento.common.messaging.outcome;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-JVM {@link ServiceCommandOutcomeStore}: tests, and one-instance services whose retries
 * always come back to the same process while it lives. It does not survive a restart and cannot
 * join a transaction, so it closes the window only for a broker-side failure (append, server
 * death) while the handler's process stays up.
 */
public final class InMemoryServiceCommandOutcomeStore implements ServiceCommandOutcomeStore {

    private final ConcurrentHashMap<String, StoredOutcome> outcomes = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryServiceCommandOutcomeStore() {
        this(Clock.systemUTC());
    }

    public InMemoryServiceCommandOutcomeStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<StoredOutcome> find(String idempotencyKey) {
        return Optional.ofNullable(outcomes.get(idempotencyKey));
    }

    @Override
    public boolean record(String idempotencyKey, String commandType, byte[] serializedResult) {
        var outcome = new StoredOutcome(idempotencyKey, commandType,
                serializedResult == null ? null : serializedResult.clone(), clock.instant());
        return outcomes.putIfAbsent(idempotencyKey, outcome) == null;
    }

    @Override
    public int sweepBefore(Instant threshold) {
        int[] removed = {0};
        outcomes.values().removeIf(o -> {
            boolean old = o.recordedAt().isBefore(threshold);
            if (old) removed[0]++;
            return old;
        });
        return removed[0];
    }

    /** How many outcomes are recorded. */
    public int size() {
        return outcomes.size();
    }
}
