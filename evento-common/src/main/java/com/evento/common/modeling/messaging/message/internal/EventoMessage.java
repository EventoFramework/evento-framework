package com.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

/**
 * The EventoMessage class represents a message in the Evento system.
 */
public class EventoMessage implements Serializable {

    private String sourceBundleId;
    private String sourceInstanceId;
    private long sourceBundleVersion;
    private Serializable body;

    /**
     * Returns the source bundle ID of the EventoMessage.
     *
     * @return the source bundle ID of the EventoMessage as a String
     */
    public String getSourceBundleId() {
        return sourceBundleId;
    }

    /**
     * Sets the source bundle ID of the EventoMessage.
     *
     * @param sourceBundleId the source bundle ID to set for the EventoMessage
     */
    public void setSourceBundleId(String sourceBundleId) {
        this.sourceBundleId = sourceBundleId;
    }

    /**
     * Returns the source instance ID of the EventoMessage.
     *
     * @return the source instance ID of the EventoMessage as a String
     */
    public String getSourceInstanceId() {
        return sourceInstanceId;
    }

    /**
     * Sets the source instance ID of the EventoMessage.
     *
     * @param sourceInstanceId the source instance ID to set for the EventoMessage
     */
    public void setSourceInstanceId(String sourceInstanceId) {
        this.sourceInstanceId = sourceInstanceId;
    }

    /**
     * Returns the source bundle version of the EventoMessage.
     *
     * @return the source bundle version of the EventoMessage as a long
     */
    public long getSourceBundleVersion() {
        return sourceBundleVersion;
    }

    /**
     * Sets the source bundle version of the EventoMessage.
     *
     * @param sourceBundleVersion the source bundle version to set for the EventoMessage
     */
    public void setSourceBundleVersion(long sourceBundleVersion) {
        this.sourceBundleVersion = sourceBundleVersion;
    }

    /**
     * Returns the body of the EventoMessage.
     *
     * @return the body of the EventoMessage as a Serializable object
     */
    public Serializable getBody() {
        return body;
    }

    /**
     * Sets the body of the EventoMessage.
     *
     * @param body the body to set for the EventoMessage
     */
    public void setBody(Serializable body) {
        this.body = body;
    }
}
