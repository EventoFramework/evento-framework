package com.evento.common.modeling.messaging.message.internal.consumer;

import com.evento.common.modeling.bundle.types.ComponentType;

import java.io.Serializable;


/**
 * The ConsumerFetchStatusRequestMessage class represents a message used to request the status of a consumer.
 * The status request message includes the consumer ID.
 *
 * <p>This class implements the Serializable interface to enable serialization and deserialization of objects of this class.</p>
 *
 * <p>Use the constructor {@link #ConsumerFetchStatusRequestMessage(String)} to create an instance of the ConsumerFetchStatusRequestMessage class with the specified consumer ID.
 * The default constructor {@link #ConsumerFetchStatusRequestMessage()} can be used to create an instance with no consumer ID specified.</p>
 *
 * <p>After creating an instance of the ConsumerFetchStatusRequestMessage class, the consumer ID can be retrieved or modified using the getter {@link #getConsumerId()} and setter
 *  {@link #setConsumerId(String)} respectively.</p>
 *
 */
public class ConsumerFetchStatusRequestMessage implements Serializable {
    private String consumerId;
    private ComponentType componentType;

    public ConsumerFetchStatusRequestMessage(String consumerId, ComponentType componentType) {
        this.consumerId = consumerId;
        this.componentType = componentType;
    }

    public ConsumerFetchStatusRequestMessage() {
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
