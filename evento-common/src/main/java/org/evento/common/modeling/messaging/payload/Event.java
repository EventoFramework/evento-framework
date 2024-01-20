package org.evento.common.modeling.messaging.payload;

import org.evento.common.utils.Context;

/**
 * The Event class represents an abstract base class for events in a software system.
 * It extends the Payload class.
 *
 * @see Payload
 */
public abstract class Event extends Payload {
    private String context = Context.DEFAULT;

    /**
     * Returns the context of the event.
     *
     * The context is a string value representing the available context options for certain functionalities within a software system.
     * It is set by calling the setContext method.
     * The context can be accessed using the getContext method.
     *
     * @return the context of the event as a string
     *
     * @see #setContext(String)
     * @see Event#setContext(String)
     */
    public String getContext() {
        return context;
    }

    /**
     * Sets the context of the event.
     * The context is a string value representing the available context options for certain functionalities within a software system.
     * It is set by calling the setContext method.
     *
     * @param context the context to be set as a string
     * @throws IllegalArgumentException if the context provided is null
     * @return the updated event object with the new context
     *
     * @see Event#getContext()
     * @see Event#setContext(String)
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> T setContext(String context) {
        if(context ==  null){
            throw new IllegalArgumentException();
        }
        this.context = context;
        return (T) this;
    }
}
