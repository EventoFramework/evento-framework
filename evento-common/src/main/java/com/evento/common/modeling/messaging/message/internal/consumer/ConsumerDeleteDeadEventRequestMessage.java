package com.evento.common.modeling.messaging.message.internal.consumer;


import com.evento.common.modeling.bundle.types.ComponentType;

import java.io.Serializable;


/**
 * The ConsumerDeleteDeadEventRequestMessage class represents a request message to delete a dead event for a consumer.
 * It implements the Serializable interface to support serialization of objects.
 * This class contains the consumer ID, component type, and event sequence number.
 *
 * @since 1.0
 */
public class ConsumerDeleteDeadEventRequestMessage implements Serializable {
    private String consumerId;
    private ComponentType componentType;
    private long eventSequenceNumber;


    /**
     * The ConsumerDeleteDeadEventRequestMessage class represents a request message to delete a dead event for a consumer.
     *
     * This class contains the consumer ID, component type, and event sequence number.
     *
     * @param consumerId The consumer ID.
     * @param componentType The component type of the object.
     * @param eventSequenceNumber The event sequence number.
     */
    public ConsumerDeleteDeadEventRequestMessage(String consumerId, ComponentType componentType, long eventSequenceNumber) {
        this.consumerId = consumerId;
        this.componentType = componentType;
        this.eventSequenceNumber = eventSequenceNumber;
    }

    public ConsumerDeleteDeadEventRequestMessage() {
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
