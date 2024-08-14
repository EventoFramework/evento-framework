package com.evento.application.consumer;

import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.messaging.consumer.DeadPublishedEvent;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;

/**
 * Abstract class representing an event consumer.
 */
public abstract class EventConsumer implements Runnable {

    protected static final Logger logger = LogManager.getLogger(EventConsumer.class);

    @Getter
    protected final String consumerId;
    protected final ConsumerStateStore consumerStateStore;

    protected EventConsumer(String consumerId, ConsumerStateStore consumerStateStore) {
        this.consumerId = consumerId;
        this.consumerStateStore = consumerStateStore;
    }

    /**
     * An abstract method that represents the consumption of a dead event queue.
     *
     * @throws Exception if an error occurs during the consumption of the dead event queue
     */
    public abstract void consumeDeadEventQueue() throws Exception;


    /**
     * Retrieves the dead event queue for the current event consumer.
     *
     * This method calls the {@code getEventsFromDeadEventQueue} method of the {@code ConsumerStateStore} object
     * with the consumer ID provided during the creation of the {@code EventConsumer} object.
     * The returned dead event queue represents a collection of {@code DeadPublishedEvent} objects that were moved to the dead event queue for further handling.
     *
     * @return a Collection of {@code DeadPublishedEvent} objects representing the dead event queue
     * @throws Exception if an error occurs during the retrieval of the dead event queue
     * @see ConsumerStateStore#getEventsFromDeadEventQueue(String)
     * @see DeadPublishedEvent
     */
    public Collection<DeadPublishedEvent> getDeadEventQueue() throws Exception {
        return consumerStateStore.getEventsFromDeadEventQueue(consumerId);
    }


    /**
     * Retrieves the last consumed event sequence number for the event consumer.
     * The last consumed event sequence number represents the highest event sequence number processed by the consumer.
     *
     * This method calls the {@code getLastEventSequenceNumberSagaOrHead} method of the {@code consumerStateStore} object
     * with the consumer ID provided during the creation of the {@code EventConsumer} object.
     * If there is no last consumed event sequence number stored for the consumer, it retrieves the last event sequence number from the {@code eventoServer} and stores it as the last
     *  consumed event sequence number.
     * If the last consumed event sequence number is already stored, it simply returns the stored value.
     *
     * @return the last consumed event sequence number for the event consumer
     * @throws Exception if an error occurs during the retrieval of the last consumed event sequence number
     * @see ConsumerStateStore#getLastEventSequenceNumberSagaOrHead(String)
     */
    public long getLastConsumedEvent() throws Exception {
        return consumerStateStore.getLastEventSequenceNumberSagaOrHead(consumerId);
    }


    /**
     * Sets the retry flag for a dead event of a specific consumer.
     *
     * @param eventSequenceNumber the sequence number of the dead event
     * @param retry               the retry flag, true if the event should be retried, false otherwise
     * @throws Exception if an error occurs during the retry flag setting
     */
    public void setDeadEventRetry(long eventSequenceNumber, boolean retry) throws Exception {
        logger.trace("Set retry: {} - {} - {}", consumerId, eventSequenceNumber, retry);
        consumerStateStore.setRetryDeadEvent(consumerId, eventSequenceNumber, retry);
    }

    public void deleteDeadEvent(long eventSequenceNumber) throws Exception {
        logger.trace("Delete Event: {} - {}", consumerId, eventSequenceNumber);
        consumerStateStore.removeEventFromDeadEventQueue(consumerId, eventSequenceNumber);
    }
}
