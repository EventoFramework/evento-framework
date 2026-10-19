package com.evento.server.es;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Which aggregates are contended right now, so that the broker runs their commands under the
 * aggregate lock instead of optimistically: a command that would only lose the fence to another
 * one and run again is cheaper queued on the lock.
 *
 * <p>Two signs, both cheap: another command for the same aggregate already running on this
 * server (seen when the command enters), or a fence conflict on the aggregate in the last
 * {@code memoryMs} (the other writer may be another server). A hot aggregate keeps one of them
 * up as long as it is hot; an aggregate that cools down goes back to optimistic.
 */
final class AggregateContention {

    private static final int MAX_REMEMBERED = 100_000;

    private final ConcurrentHashMap<String, Integer> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> conflictedUntil = new ConcurrentHashMap<>();
    private final long memoryMs;

    AggregateContention(long memoryMs) {
        this.memoryMs = memoryMs;
    }

    /**
     * A command for {@code aggregateId} starts here; pair with {@link #exit}.
     *
     * @return whether another one is already running here
     */
    boolean enter(String aggregateId) {
        return inFlight.merge(aggregateId, 1, Integer::sum) > 1;
    }

    void exit(String aggregateId) {
        inFlight.computeIfPresent(aggregateId, (k, n) -> n > 1 ? n - 1 : null);
    }

    /** Whether a fence conflict on {@code aggregateId} was seen in the last {@code memoryMs}. */
    boolean recentlyConflicted(String aggregateId) {
        Long until = conflictedUntil.get(aggregateId);
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        conflictedUntil.remove(aggregateId, until);
        return false;
    }

    void conflicted(String aggregateId) {
        long now = System.currentTimeMillis();
        if (conflictedUntil.size() >= MAX_REMEMBERED) {
            conflictedUntil.values().removeIf(until -> until <= now);
            if (conflictedUntil.size() >= MAX_REMEMBERED) conflictedUntil.clear();
        }
        conflictedUntil.put(aggregateId, now + memoryMs);
    }
}
