package com.evento.application.consumer;

import com.evento.common.messaging.consumer.DeadPublishedEvent;
import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerFetchStatusResponseMessage;

import java.util.Collection;

/**
 * Admin-side handle for a consumer instance, regardless of whether the
 * instance is the v1 {@link EventConsumer} abstract class or one of the
 * new v2 engines in {@code com.evento.application.consumer.v2}.
 *
 * <p>Introduced in slice 3.4 so {@code BundleAdminRequestHandler.ConsumerLookup}
 * can return v1 or v2 engines uniformly without leaking the {@code EventConsumer}
 * abstract class (slated for deletion in slice 3.5).
 *
 * <p>The method set is exactly the surface the admin dispatcher pulls on:
 * status snapshot, DLQ retrieval / retry / delete, manual dead-event drain,
 * and the consumer id for routing.
 */
public interface ConsumerHandle {

    /** Stable identifier for this consumer instance (used for admin routing). */
    String getConsumerId();

    /** Status snapshot served to dashboard / discovery callers. */
    ConsumerFetchStatusResponseMessage toConsumerStatus();

    /** Last sequence number processed by this consumer (or HEAD if never seeded). */
    long getLastConsumedEvent() throws Exception;

    /** Dead-letter queue snapshot. */
    Collection<DeadPublishedEvent> getDeadEventQueue() throws Exception;

    /** Flip the retry flag on a DLQ entry. */
    void setDeadEventRetry(long eventSequenceNumber, boolean retry) throws Exception;

    /** Permanently remove a DLQ entry. */
    void deleteDeadEvent(long eventSequenceNumber) throws Exception;

    /** Replay retriable DLQ entries through the consumer's handlers. */
    void consumeDeadEventQueue() throws Exception;
}
