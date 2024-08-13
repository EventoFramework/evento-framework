package com.evento.common.messaging.consumer;

import com.evento.common.modeling.messaging.dto.PublishedEvent;

import java.time.ZonedDateTime;

/**
 * The DeadPublishedEvent class represents a dead published event in the event sourcing architecture.
 * A dead published event occurs when a published event fails to be processed and is moved to a dead event queue for further handling.
 * It contains information about the consumer ID, event sequence number, event message, retry status, and timestamp when it was moved to the dead event queue.
 *
 * This class provides a constructor to create a new DeadPublishedEvent object with the specified consumer ID, event sequence number, event message, retry status, and dead timestamp
 * .
 */
public class DeadPublishedEvent {

    private String consumerId;
    private String eventName;
    private String aggregateId;
    private long eventSequenceNumber;
    private PublishedEvent event;
    private boolean retry;
    private ZonedDateTime deadAt;

    /**
     * The DeadPublishedEvent class represents a dead published event in the event sourcing architecture.
     * A dead published event occurs when a published event fails to be processed and is moved to a dead event queue for further handling.
     * It contains information about the consumer ID, event name, aggregate ID, event sequence number, event message, retry status, and timestamp when it was moved to the dead event
     *  queue.
     *
     * This class provides a constructor to create a new DeadPublishedEvent object with the specified consumer ID, event name, aggregate ID, event sequence number, event message,
     *  retry status, and dead timestamp.
     *
     * @param consumerId The ID of the consumer that failed to process the published event.
     * @*/
    public DeadPublishedEvent(String consumerId, String eventName, String aggregateId, long eventSequenceNumber, PublishedEvent event, boolean retry, ZonedDateTime deadAt) {
        this.consumerId = consumerId;
        this.eventName = eventName;
        this.aggregateId = aggregateId;
        this.eventSequenceNumber = eventSequenceNumber;
        this.event = event;
        this.retry = retry;
        this.deadAt = deadAt;
    }


    public DeadPublishedEvent() {
    }

    /**
     * Retrieves the event of the DeadPublishedEvent.
     *
     * @return the event of the DeadPublishedEvent as a PublishedEvent object.
     * @see PublishedEvent
     */
    public PublishedEvent getEvent() {
        return event;
    }

    /**
     * Sets the event of the DeadPublishedEvent.
     *
     * @param event the event to be set as a PublishedEvent object
     *
     * @see PublishedEvent
     */
    public void setEvent(PublishedEvent event) {
        this.event = event;
    }

    /**
     * Retrieves the retry status of the DeadPublishedEvent.
     *
     * @return the retry status of the DeadPublishedEvent as a boolean value*/
    public boolean isRetry() {
        return retry;
    }

    /**
     * Sets the retry status of the DeadPublishedEvent.
     *
     * @param retry the retry status to be set as a boolean value
     */
    public void setRetry(boolean retry) {
        this.retry = retry;
    }

    /**
     * Retrieves the dead timestamp of the DeadPublishedEvent.
     *
     * @return the dead timestamp of the DeadPublishedEvent as a ZonedDateTime object.
     * @see ZonedDateTime
     */
    public ZonedDateTime getDeadAt() {
        return deadAt;
    }

    /**
     * Sets the dead timestamp of the DeadPublishedEvent.
     *
     * @param deadAt the*/
    public void setDeadAt(ZonedDateTime deadAt) {
        this.deadAt = deadAt;
    }

    /**
     * Retrieves the consumer ID of the DeadPublishedEvent.
     *
     * @return the consumer ID of the DeadPublishedEvent as a String.
     */
    public String getConsumerId() {
        return consumerId;
    }

    /**
     * Sets the consumer ID for the DeadPublishedEvent.
     *
     * @param consumerId the consumer ID to be set as a String
     */
    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    /**
     * Retrieves the event sequence number of the DeadPublishedEvent.
     *
     * @return the event sequence number of the DeadPublishedEvent as a long value.
     */
    public long getEventSequenceNumber() {
        return eventSequenceNumber;
    }

    /**
     * Sets the event sequence number for the DeadPublishedEvent.
     *
     * @param eventSequenceNumber the event sequence number to be set as a long value.
     * @see DeadPublishedEvent#getEventSequenceNumber()
     */
    public void setEventSequenceNumber(long eventSequenceNumber) {
        this.eventSequenceNumber = eventSequenceNumber;
    }

    /**
     * Retrieves the name of the event associated with the DeadPublishedEvent.
     *
     * @return the name of the event as a String
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * Sets the name of the event associated with the DeadPublishedEvent.
     *
     * @param eventName the name of the event to be set as a String
     */
    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    /**
     * Retrieves the aggregate ID of the DeadPublishedEvent.
     *
     * @return the aggregate ID of the DeadPublishedEvent as a String.
     */
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * Sets the aggregate ID of the DeadPublishedEvent.
     *
     * The aggregate ID is a unique identifier for an aggregate in the event sourcing architecture. It is typically a string value that uniquely identifies an aggregate instance.
     *
     * This method allows you to set the aggregate ID for a DeadPublishedEvent object. Once set, the aggregate ID can be retrieved using the getAggregateId method.
     *
     * @param aggregateId the aggregate ID to be set as a String
     *
     * @see DeadPublishedEvent#getAggregateId()
     * @see PublishedEvent#getAggregateId()
     */
    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }
}
