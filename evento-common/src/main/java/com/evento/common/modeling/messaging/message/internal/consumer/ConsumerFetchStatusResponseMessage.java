package com.evento.common.modeling.messaging.message.internal.consumer;

import com.evento.common.messaging.consumer.DeadPublishedEvent;

import java.io.Serializable;
import java.util.Collection;


/**
 * The ConsumerFetchStatusResponseMessage class represents a response message containing the status of a consumer's fetch operation.
 * It implements the Serializable interface to allow objects of this class to be serialized and deserialized.
 * <p>
 * This class provides methods to retrieve and set the last event sequence number and the collection of dead events.
 */
public class ConsumerFetchStatusResponseMessage implements Serializable {
    private long lastEventSequenceNumber;
    private Collection<DeadPublishedEvent> deadEvents;

    /**
     * Retrieves the last event sequence number.
     * <p>
     * This method returns the last event sequence number of the ConsumerFetchStatusResponseMessage.
     * The last event sequence number represents the sequence number of the last event that was fetched by the consumer.
     *
     * @return the last event sequence number as a long value.
     */
    public long getLastEventSequenceNumber() {
        return lastEventSequenceNumber;
    }

    /**
     * Sets the last event sequence number.
     * <p>
     * This method allows you to set the last event sequence number of the ConsumerFetchStatusResponseMessage.
     * The last event sequence number represents the sequence number of the last event that was fetched by the consumer.
     *
     * @param lastEventSequenceNumber the last event sequence number to be set, as a long value.
     */
    public void setLastEventSequenceNumber(long lastEventSequenceNumber) {
        this.lastEventSequenceNumber = lastEventSequenceNumber;
    }

    /**
     * Retrieves the collection of dead published events.
     * <p>
     * This method returns the collection of DeadPublishedEvent objects that represent dead published events in the event sourcing architecture. A dead published event occurs when
     *  a published event fails to be processed and is moved to a dead event queue for further handling.
     *
     * @return the collection of DeadPublishedEvent objects representing the dead published events.
     * @see DeadPublishedEvent
     */
    public Collection<DeadPublishedEvent> getDeadEvents() {
        return deadEvents;
    }

    /**
     * Sets the collection of dead published events.
     * <p>
     * This method allows you to set the collection of dead published events for the ConsumerFetchStatusResponseMessage object.
     * A dead published event occurs when a published event fails to be processed and is moved to a dead event queue for further handling.
     *
     * @param deadEvents the collection of DeadPublishedEvent objects representing the dead published events to be set.
     * @see DeadPublishedEvent
     */
    public void setDeadEvents(Collection<DeadPublishedEvent> deadEvents) {
        this.deadEvents = deadEvents;
    }
}
