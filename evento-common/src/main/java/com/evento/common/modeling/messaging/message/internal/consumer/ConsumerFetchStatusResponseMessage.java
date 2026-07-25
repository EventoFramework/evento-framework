package com.evento.common.modeling.messaging.message.internal.consumer;

import com.evento.common.messaging.consumer.DeadPublishedEvent;

import java.io.Serializable;
import java.time.ZonedDateTime;
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
    private boolean isInError;
    private ZonedDateTime errorStartAt;
    private ZonedDateTime lastErrorAt;
    private long errorCount;
    private String error;
    private boolean enabled;

    // --- Async consumers (@EventHandler(executor = "...")) -------------------
    // Additive: AdminPayloadCodec disables FAIL_ON_UNKNOWN_PROPERTIES, so an older
    // server decoding a newer bundle's response simply ignores these.

    private int asyncInFlight;
    private long asyncSubmitTimeouts;
    private long asyncTransientFailures;
    private Collection<String> asyncExecutors;

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

    public boolean isInError() {
        return isInError;
    }

    public void setInError(boolean inError) {
        isInError = inError;
    }

    public ZonedDateTime getErrorStartAt() {
        return errorStartAt;
    }

    public void setErrorStartAt(ZonedDateTime errorStartAt) {
        this.errorStartAt = errorStartAt;
    }

    public ZonedDateTime getLastErrorAt() {
        return lastErrorAt;
    }

    public void setLastErrorAt(ZonedDateTime lastErrorAt) {
        this.lastErrorAt = lastErrorAt;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Async handler tasks dispatched by this consumer and not yet finished. */
    public int getAsyncInFlight() {
        return asyncInFlight;
    }

    public void setAsyncInFlight(int asyncInFlight) {
        this.asyncInFlight = asyncInFlight;
    }

    /**
     * How often this consumer had to end a cycle because its executor had no capacity.
     * A climbing value means the executor is the bottleneck.
     */
    public long getAsyncSubmitTimeouts() {
        return asyncSubmitTimeouts;
    }

    public void setAsyncSubmitTimeouts(long asyncSubmitTimeouts) {
        this.asyncSubmitTimeouts = asyncSubmitTimeouts;
    }

    /**
     * Transient failures in async handlers. These dead-letter rather than redeliver,
     * because the checkpoint has already advanced past them.
     */
    public long getAsyncTransientFailures() {
        return asyncTransientFailures;
    }

    public void setAsyncTransientFailures(long asyncTransientFailures) {
        this.asyncTransientFailures = asyncTransientFailures;
    }

    /** Names of the executors this consumer's handlers dispatch to; empty if fully inline. */
    public Collection<String> getAsyncExecutors() {
        return asyncExecutors;
    }

    public void setAsyncExecutors(Collection<String> asyncExecutors) {
        this.asyncExecutors = asyncExecutors;
    }
}
