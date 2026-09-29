package com.evento.lab.cluster.support.store;

/**
 * One database the global lane fans a price change out to. Implementations
 * must be idempotent by event sequence: a change is applied only if its
 * {@code seq} is above the sequence the target already holds for that SKU,
 * so a redelivered event (the lane retries after a partial failure) is a
 * {@link Outcome#STALE} no-op on the targets that already have it.
 */
public interface PriceTarget {

    enum Outcome {
        /** Written: the target held an older sequence, or none. */
        APPLIED,
        /** Skipped: the target already held this or a newer sequence (a replay). */
        STALE
    }

    /** Target name, e.g. {@code eu}. */
    String name();

    /**
     * Apply one change. Throws when the target cannot be reached or the write
     * fails; the caller keeps going with the other targets and retries later.
     */
    Outcome apply(PriceChange change) throws Exception;
}
