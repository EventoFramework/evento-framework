package com.evento.common.messaging.consumer.v2;

import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.modeling.messaging.dto.PublishedEvent;

import java.util.Collection;

/**
 * Per-consumer queue for events whose handler threw. The dashboard surfaces
 * these to operators who can mark them for retry or delete; the
 * {@code consumeDeadEventsForXxx} loops on {@link com.evento.common.messaging.consumer.v2.ConsumerProcessor}
 * walk the retriable subset.
 *
 * <p>Primary key is {@code (consumerId, eventSequenceNumber)} — the same event
 * can be dead-lettered by many consumers independently.
 */
public interface DeadEventQueue {

    /** Insert (or upsert) the failed event with its cause. {@code retry} starts false. */
    void add(String consumerId, PublishedEvent event, Throwable cause);

    /** Drop a specific dead entry. Idempotent. */
    void remove(String consumerId, long eventSequenceNumber);

    /** Drop a specific dead entry. Convenience for the dead-event consume loops. */
    default void remove(String consumerId, PublishedEvent event) {
        remove(consumerId, event.getEventSequenceNumber());
    }

    /** Events the operator has marked for retry — the consume loop picks these up. */
    Collection<PublishedEvent> getRetriable(String consumerId);

    /** Every dead entry for this consumer (dashboard view). */
    Collection<DeadPublishedEvent> getAll(String consumerId);

    /** Operator flips {@code retry} so the next dead-event cycle reprocesses it. */
    void setRetry(String consumerId, long eventSequenceNumber, boolean retry);
}
