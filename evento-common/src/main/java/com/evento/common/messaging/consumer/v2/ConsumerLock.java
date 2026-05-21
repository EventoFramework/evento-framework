package com.evento.common.messaging.consumer.v2;

import java.util.Optional;

/**
 * Cross-instance, per-consumer exclusive lock. Replaces the v1
 * {@code enterExclusiveZone}/{@code leaveExclusiveZone} pair as a SOLID-clean
 * {@link AutoCloseable} handle.
 *
 * <p>The lock is the load-bearing piece of consumer correctness: only one
 * instance of the same {@code consumerId} (typically
 * {@code <bundleName>#<context>#<consumerName>}) processes events at a time.
 * On Postgres it's a session-scoped {@code pg_advisory_lock(hashtext(consumerId))};
 * on MySQL it's {@code GET_LOCK(consumerId, 0)}.
 */
public interface ConsumerLock extends AutoCloseable {

    /**
     * Try to take the lock without blocking. Empty means another instance holds
     * it — the caller must skip this cycle and try again later. Held means the
     * caller has exclusive ownership until {@link LockHandle#close()}.
     */
    Optional<LockHandle> tryAcquire(String consumerId);

    /** Handle returned by a successful {@link #tryAcquire}. Release by closing. */
    interface LockHandle extends AutoCloseable {

        String consumerId();

        /** Release the lock. Idempotent. */
        @Override
        void close();
    }

    @Override
    default void close() {
        // Default: nothing to release. JDBC impls override.
    }
}
