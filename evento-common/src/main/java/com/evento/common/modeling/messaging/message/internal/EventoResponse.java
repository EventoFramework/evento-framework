package com.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

/**
 * The EventoResponse class represents a response in the Evento messaging system.
 * It contains the correlation ID, body, and timestamp of the response.
 */
public class EventoResponse implements Serializable {

    private String correlationId;
    private Serializable body;

    private long timestamp;

    /**
     * Returns the body of the EventoResponse.
     *
     * @return The body of the EventoResponse.
     */
    public Serializable getBody() {
        return body;
    }

    /**
     * Sets the body of the EventoResponse.
     *
     * @param body The body of the EventoResponse.
     */
    public void setBody(Serializable body) {
        this.body = body;
    }

    /**
     * Retrieves the correlation ID of the EventoResponse.
     *
     * @return The correlation ID of the EventoResponse.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Sets the correlation ID of the EventoResponse.
     *
     * @param correlationId The correlation ID to set.
     */
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    /**
     * Retrieves the timestamp of the EventoResponse.
     *
     * @return The timestamp of the EventoResponse.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp of the EventoResponse.
     *
     * @param timestamp The timestamp to set.
     * @return The updated EventoResponse object.
     */
    public EventoResponse setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }
}
