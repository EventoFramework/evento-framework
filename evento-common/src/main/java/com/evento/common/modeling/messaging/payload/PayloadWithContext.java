package com.evento.common.modeling.messaging.payload;

import com.evento.common.utils.Context;

/**
 * PayloadWithContext is an abstract class that represents a payload object with context information.
 * It extends the Payload interface.
 */
public abstract class PayloadWithContext implements Payload {
    private String context = Context.DEFAULT;

    /**
     * Represents a flag indicating whether telemetry should be forced.
     *
     * Telemetry refers to the collection of data about the operation and performance of a software system.
     * When this flag is set to true, it forces the system to collect telemetry data even if it is disabled by default.
     * When this flag is set to false, the system will use the default behavior for telemetry data collection.
     *
     * This flag is used in the context of a software system and is set in the PayloadWithContext class.
     *
     * Example usage:
     * PayloadWithContext payload = new MyPayload().setForceTelemetry(true);
     * boolean forceTelemetry = payload.isForceTelemetry();
     *
     * @see PayloadWithContext
     */
    private boolean forceTelemetry = false;

    /**
     * Retrieves the context of the current object.
     * <p>
     * The context is a string value representing the available context options for certain functionalities within a software system.
     * It is set by calling the setContext method of the object.
     * The context can be accessed using the getContext method of the object.
     *
     * @return the context of the object as a string
     *
     * @see PayloadWithContext#setContext(String)
     * @see PayloadWithContext#getContext()
     */
    public String getContext() {
        return context;
    }

    /**
     * Retrieves the ID of the aggregate that the payload is targeting.
     *
     * @return The aggregate ID as a string.
     */
    public abstract String getAggregateId();

    /**
     * Sets the context of the object.
     * <p>
     * The context is a string value representing the available context options for certain functionalities within a software system.
     * It is set by calling the setContext method of the object.
     *
     * @param context the context to be set as a string
     * @param <T>     the type of the payload with context
     * @return the object itself
     * @throws IllegalArgumentException if the context is null
     *
     * @see PayloadWithContext#getContext()
     * @see PayloadWithContext#setContext(String)
     * @see Event#setContext(String)
     */
    @SuppressWarnings("unchecked")
    public <T extends PayloadWithContext> T setContext(String context) {
        if(context ==  null){
            throw new IllegalArgumentException();
        }
        this.context = context;
        return (T) this;
    }

    /**
     * Checks if telemetry is forced for the current payload.
     *
     * Telemetry refers to the collection of data about the operation and performance of a software system.
     * When telemetry is forced, it overrides the default behavior of telemetry data collection.
     *
     * @return true if telemetry is forced, false otherwise
     *
     * @see PayloadWithContext
     */
    public boolean isForceTelemetry() {
        return forceTelemetry;
    }

    /**
     * Sets whether telemetry should be forced for the current payload.
     *
     * Telemetry refers to the collection of data about the operation and performance of a software system.
     * When telemetry is forced, it overrides the default behavior of telemetry data collection.
     *
     * @param forceTelemetry true to force telemetry, false otherwise
     * @param <T> the type of the payload with context
     * @return the payload itself with the force telemetry flag set
     */
    @SuppressWarnings("unchecked")
    public <T extends PayloadWithContext> T  setForceTelemetry(boolean forceTelemetry) {
        this.forceTelemetry = forceTelemetry;
        return (T) this;
    }
}
