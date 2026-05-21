package com.evento.common.messaging.consumer.v2;

import java.time.Instant;

/**
 * Per-(consumer, event-id) exactly-once gate for observer consumers and
 * anything else that needs "did I already see this event?" semantics.
 *
 * <p>Implementations are expected to be effectively unbounded in the sense
 * that any claimed key stays claimed at least until {@link #sweepBefore} drops
 * it. The pair {@code (consumerId, eventId)} is the natural primary key — a
 * single eventId can be claimed by many consumers independently.
 */
public interface DedupeStore extends AutoCloseable {

    /**
     * Try to mark {@code eventId} as seen by {@code consumerId}. Atomic across
     * concurrent callers — exactly one wins, the rest get {@code false}.
     *
     * @return {@code true} if the caller claimed it (must process), {@code false}
     *         if someone already did (must skip).
     */
    boolean tryClaim(String consumerId, String eventId);

    /**
     * Release a previously claimed key. Used by engines that want to retry on
     * failure rather than have the event sit in a dead-letter queue.
     */
    void release(String consumerId, String eventId);

    /**
     * Drop entries claimed strictly before {@code threshold}. Returns the number
     * of rows removed so callers can log it.
     */
    int sweepBefore(Instant threshold);

    @Override
    default void close() {
        // Default: nothing to release.
    }
}
