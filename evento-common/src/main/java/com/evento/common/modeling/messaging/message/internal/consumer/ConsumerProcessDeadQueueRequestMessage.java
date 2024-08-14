package com.evento.common.modeling.messaging.message.internal.consumer;

import com.evento.common.modeling.bundle.types.ComponentType;

import java.io.Serializable;



public class ConsumerProcessDeadQueueRequestMessage implements Serializable {
    private String consumerId;
    private ComponentType componentType;

    public ConsumerProcessDeadQueueRequestMessage(String consumerId, ComponentType componentType) {
        this.consumerId = consumerId;
        this.componentType = componentType;
    }

    public ConsumerProcessDeadQueueRequestMessage() {
    }

    /**
     * Retrieves the consumer ID.
     *
     * @return The consumer ID.
     */
    public String getConsumerId() {
        return consumerId;
    }

    /**
     * Sets the consumer ID for the ConsumerFetchStatusRequestMessage.
     *
     * @param consumerId The consumer ID to be set.
     */
    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    /**
     * Retrieves the component type of the ConsumerFetchStatusRequestMessage.
     *
     * @return The component type of the ConsumerFetchStatusRequestMessage.
     */
    public ComponentType getComponentType() {
        return componentType;
    }

    /**
     * Sets the component type of the {@code ConsumerFetchStatusRequestMessage}.
     *
     * @param componentType The component type to be set.
     */
    public void setComponentType(ComponentType componentType) {
        this.componentType = componentType;
    }
}
