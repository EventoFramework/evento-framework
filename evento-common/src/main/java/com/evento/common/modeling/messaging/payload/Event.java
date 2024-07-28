package com.evento.common.modeling.messaging.payload;

import com.evento.common.utils.Context;

/**
 * The Event class represents an abstract base class for events in a software system.
 * It extends the Payload class.
 *
 * @see Payload
 */
public abstract class Event extends PayloadWithContext {

    private String aggregateId;

    @Override
    public String getAggregateId() {
        return aggregateId;
    }

    /**
     * Sets the aggregate ID for the event.
     *
     * @param aggregateId the aggregate ID to be set
     * @param <T>         the type of the event
     * @return the event itself with the aggregate ID set
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> T setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
        return (T) this;
    }

    /**
     * Sets the aggregate ID for the event based on the provided payload.
     * It updates the aggregate ID of the current event object with the aggregate ID from the payload.
     * The aggregate ID is retrieved from the payload by calling the getAggregateId method.
     * This method is automatically called by EventoServer when an event is fired from a command
     *
     * @param payload the payload containing the aggregate ID to be set
     * @param <T>     the type of the event
     * @return the event itself with the updated aggregate ID
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> T setAggregateId(PayloadWithContext payload) {
        this.aggregateId = payload.getAggregateId();
        return (T) this;
    }
}
