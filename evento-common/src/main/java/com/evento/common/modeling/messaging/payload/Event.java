package com.evento.common.modeling.messaging.payload;

import com.evento.common.utils.Context;

/**
 * The Event class represents an abstract base class for events in a software system.
 * It extends the Payload class.
 *
 * @see Payload
 */
public abstract class Event extends TrackablePayload {

    private String context = Context.DEFAULT;


    /**
     * Retrieves the context of the object.
     * <p>
     * The context is a string value representing the available context options for certain functionalities within a software system.
     * It is set by calling the setContext method of the object.
     *
     * @return the context of the object as a string
     */
    public String getContext() {
        return context;
    }

    /**
     * Sets the context of the Event object.
     * <p>
     * The context is a string value representing the available context options for certain functionalities within a software system.
     * It is set by calling the setContext method of the Event object.
     *  */
    @SuppressWarnings("unchecked")
    public <T extends Event> T setContext(String context) {
        if(context ==  null){
            throw new IllegalArgumentException();
        }
        this.context = context;
        return (T) this;
    }
}
