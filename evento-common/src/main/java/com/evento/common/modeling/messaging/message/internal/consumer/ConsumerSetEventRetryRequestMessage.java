package com.evento.common.modeling.messaging.message.internal.consumer;


import com.evento.common.modeling.bundle.types.ComponentType;

import java.io.Serializable;

/**
 * The ConsumerSetEventRetryRequestMessage class represents a request message to set the retry status of an event for a consumer.
 * It is used to communicate the consumer ID, component type, event sequence number, and retry status to other components in the system.
 *
 * <p>The {@code ConsumerSetEventRetryRequestMessage} class implements the {@code Serializable} interface,
 * allowing instances of this class to be serialized and deserialized.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ConsumerSetEventRetryRequestMessage request = new ConsumerSetEventRetryRequestMessage(consumerId, componentType, eventSequenceNumber, retry);
 * consumerService.setRetryForConsumerEvent(request);
 * }</pre>
 *
 * @see Serializable
 */
public class ConsumerSetEventRetryRequestMessage implements Serializable {
    private String consumerId;
    private ComponentType componentType;
    private long eventSequenceNumber;
    private boolean retry;

    /**
     * The ConsumerSetEventRetryRequestMessage class represents a request message to set the retry status of an event for a consumer.
     * It is used to communicate the consumer ID, component type, event sequence number, and retry status to other components in the system.
     *
     * @param consumerId The ID of the consumer for which the retry status is being set.
     * @param componentType The component type of the consumer.
     * @param eventSequenceNumber The sequence number of the event for which the retry status is being set.
     * @param retry The retry status to be set (true or false).
     */
    public ConsumerSetEventRetryRequestMessage(String consumerId, ComponentType componentType, long eventSequenceNumber, boolean retry) {
        this.consumerId = consumerId;
        this.componentType = componentType;
        this.eventSequenceNumber = eventSequenceNumber;
        this.retry = retry;
    }

    public ConsumerSetEventRetryRequestMessage() {
    }

    /**
     * Returns the consumer ID.
     *
     * @return The consumer ID.
     */
    public String getConsumerId() {
        return consumerId;
    }

    /**
     * Sets the consumer ID.
     *
     * @param consumerId The new consumer ID to be set.
     */
    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    /**
     * Returns the event sequence number.
     *
     * @return The event sequence number.
     */
    public long getEventSequenceNumber() {
        return eventSequenceNumber;
    }

    /**
     * Sets the sequence number of the event for which the retry status is being set.
     *
     * @param eventSequenceNumber The new sequence number to be set.
     */
    public void setEventSequenceNumber(long eventSequenceNumber) {
        this.eventSequenceNumber = eventSequenceNumber;
    }

    /**
     * Returns the retry status of the ConsumerSetEventRetryRequestMessage.
     *
     * @return The retry status. It is true if the retry status is set, false otherwise.
     */
    public boolean isRetry() {
        return retry;
    }

    /**
     * Sets the retry status of the ConsumerSetEventRetryRequestMessage.
     *
     * @param retry The retry status to be set. Set to true to enable retry, false to disable retry.
     */
    public void setRetry(boolean retry) {
        this.retry = retry;
    }

    /**
     * Returns the component type of the object.
     *
     * @return The component type of the object.
     */
    public ComponentType getComponentType() {
        return componentType;
    }

    /**
     * Sets the component type of the object.
     *
     * @param componentType The new component type to be set.
     */
    public void setComponentType(ComponentType componentType) {
        this.componentType = componentType;
    }
}
