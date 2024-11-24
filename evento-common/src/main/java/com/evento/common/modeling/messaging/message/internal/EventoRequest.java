package com.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

/**
 * This class represents an EventoRequest that holds various metadata about the request, such as the source ID,
 * correlation ID, source bundle version, request body, and a timestamp.
 */
public class EventoRequest implements Serializable {

    private String sourceBundleId;
    private String sourceInstanceId;
    private String correlationId;
    private long sourceBundleVersion;
    private Serializable body;
    private long timestamp;

    /**
     * Gets the correlation ID of the EventoRequest.
     *
     * @return correlationId.
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Sets the correlation ID of the EventoRequest.
     *
     * @param correlationId Request's correlation ID.
     */
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    /**
     * Gets the source bundle ID of the EventoRequest.
     *
     * @return sourceBundleId.
     */
    public String getSourceBundleId() {
        return sourceBundleId;
    }

    /**
     * Sets the source bundle ID of the EventoRequest.
     *
     * @param sourceBundleId Request's source bundle ID.
     */
    public void setSourceBundleId(String sourceBundleId) {
        this.sourceBundleId = sourceBundleId;
    }

    /**
     * Gets the source instance ID of the EventoRequest.
     *
     * @return sourceInstanceId.
     */
    public String getSourceInstanceId() {
        return sourceInstanceId;
    }

    /**
     * Sets the source instance ID of the EventoRequest.
     *
     * @param sourceInstanceId Request's source instance ID.
     */
    public void setSourceInstanceId(String sourceInstanceId) {
        this.sourceInstanceId = sourceInstanceId;
    }

    /**
     * Gets the source bundle version of the EventoRequest.
     *
     * @return sourceBundleVersion.
     */
    public long getSourceBundleVersion() {
        return sourceBundleVersion;
    }

    /**
     * Sets the source bundle version of the EventoRequest.
     *
     * @param sourceBundleVersion Request's source bundle version.
     */
    public void setSourceBundleVersion(long sourceBundleVersion) {
        this.sourceBundleVersion = sourceBundleVersion;
    }

    /**
     * Gets the body of the EventoRequest.
     *
     * @return body.
     */
    public Serializable getBody() {
        return body;
    }

    /**
     * Sets the body of the EventoRequest.
     *
     * @param body Request's body.
     */
    public void setBody(Serializable body) {
        this.body = body;
    }

    /**
     * Gets the timestamp of the EventoRequest.
     *
     * @return timestamp.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the timestamp of the EventoRequest and returns this.
     *
     * @param timestamp Request's timestamp.
     * @return this.
     */
    public EventoRequest setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    @Override
    public String toString() {
        return "EventoRequest{" +
                "sourceBundleId='" + sourceBundleId + '\'' +
                ", sourceInstanceId='" + sourceInstanceId + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", sourceBundleVersion=" + sourceBundleVersion +
                ", body=" + body +
                ", timestamp=" + timestamp +
                '}';
    }
}