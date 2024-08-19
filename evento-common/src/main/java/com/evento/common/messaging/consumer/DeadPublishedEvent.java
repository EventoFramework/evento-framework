package com.evento.common.messaging.consumer;

import com.evento.common.modeling.exceptions.ExceptionWrapper;
import com.evento.common.modeling.messaging.dto.PublishedEvent;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * The DeadPublishedEvent class represents a dead published event in the event sourcing architecture.
 * A dead published event occurs when a published event fails to be processed and is moved to a dead event queue for further handling.
 * It contains information about the consumer ID, event sequence number, event message, retry status, and timestamp when it was moved to the dead event queue.
 * <p>
 * This class provides a constructor to create a new DeadPublishedEvent object with the specified consumer ID, event sequence number, event message, retry status, and dead timestamp
 * .
 */
public class DeadPublishedEvent implements Serializable {

    private String consumerId;
    private String eventName;
    private String aggregateId;
    private String context;
    private String eventSequenceNumber;
    private PublishedEvent event;
    private boolean retry;
    private ExceptionWrapper exception;
    private ZonedDateTime deadAt;

    /**
     * The DeadPublishedEvent class represents an event that failed to be processed by a consumer in the event sourcing architecture.
     * It contains information about the consumer ID, event name, aggregate ID, context, event sequence number, published event,
     * retry status, exception wrapper, and timestamp when it was marked as dead.
     */
    public DeadPublishedEvent(String consumerId, String eventName, String aggregateId, String context, String eventSequenceNumber, PublishedEvent event, boolean retry, ExceptionWrapper exception, ZonedDateTime deadAt) {
        this.consumerId = consumerId;
        this.eventName = eventName;
        this.aggregateId = aggregateId;
        this.context = context;
        this.eventSequenceNumber = eventSequenceNumber;
        this.event = event;
        this.retry = retry;
        this.exception = exception;
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
    public String getEventSequenceNumber() {
        return eventSequenceNumber;
    }

    /**
     * Sets the event sequence number for the DeadPublishedEvent.
     *
     * @param eventSequenceNumber the event sequence number to be set as a long value.
     * @see DeadPublishedEvent#getEventSequenceNumber()
     */
    public void setEventSequenceNumber(String eventSequenceNumber) {
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
     * <p>
     * The aggregate ID is a unique identifier for an aggregate in the event sourcing architecture. It is typically a string value that uniquely identifies an aggregate instance.
     * <p>
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

    /**
     * Retrieves the context of the DeadPublishedEvent.
     *
     * @return the context of the DeadPublishedEvent as a String.
     */
    public String getContext() {
        return context;
    }

    /**
     * Sets the context of the DeadPublishedEvent.
     * <p>
     * This method allows you to set the context for a DeadPublishedEvent object. The context represents additional information associated with the event. It is typically a string
     *  value that provides context or details about the event.
     * @param context the context to be set as a String
     */
    public void setContext(String context) {
        this.context = context;
    }

    /**
     * Retrieves the wrapped exception.
     *
     * @return The wrapped exception.
     */
    public ExceptionWrapper getException() {
        return exception;
    }

    /**
     * Sets the exception for the current object.
     *
     * @param exception the exception to be set
     */
    public void setException(ExceptionWrapper exception) {
        this.exception = exception;
    }
}
