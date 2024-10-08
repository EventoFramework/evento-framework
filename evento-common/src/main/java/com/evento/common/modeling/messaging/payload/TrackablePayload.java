package com.evento.common.modeling.messaging.payload;

/**
 * PayloadWithContext is an abstract class that represents a payload object with context information.
 * It extends the Payload interface.
 */
public abstract class TrackablePayload implements Payload {

    private boolean forceTelemetry = false;


    /**
     * Checks if telemetry is forced for the current payload.
     * <p>
     * Telemetry refers to the collection of data about the operation and performance of a software system.
     * When telemetry is forced, it overrides the default behavior of telemetry data collection.
     *
     * @return true if telemetry is forced, false otherwise
     *
     * @see TrackablePayload
     */
    public boolean isForceTelemetry() {
        return forceTelemetry;
    }

    /**
     * Sets whether telemetry should be forced for the current payload.
     * <p> <p>
     * Telemetry refers to the collection of data about the operation and performance of a software system.
     * When telemetry is forced, it overrides the default behavior of telemetry data collection.
     *
     * @param forceTelemetry true to force telemetry, false otherwise
     * @param <T>            the type of the payload with context
     */
    public <T extends TrackablePayload> void setForceTelemetry(boolean forceTelemetry) {
        this.forceTelemetry = forceTelemetry;
    }
}
