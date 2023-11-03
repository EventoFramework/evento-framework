package org.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

public class EventoRequest implements Serializable {

    private String sourceBundleId;
    private String sourceInstanceId;
    private String correlationId;
    private long sourceBundleVersion;
    private Serializable body;

    private long timestamp;

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getSourceBundleId() {
        return sourceBundleId;
    }

    public void setSourceBundleId(String sourceBundleId) {
        this.sourceBundleId = sourceBundleId;
    }

    public String getSourceInstanceId() {
        return sourceInstanceId;
    }

    public void setSourceInstanceId(String sourceInstanceId) {
        this.sourceInstanceId = sourceInstanceId;
    }

    public long getSourceBundleVersion() {
        return sourceBundleVersion;
    }

    public void setSourceBundleVersion(long sourceBundleVersion) {
        this.sourceBundleVersion = sourceBundleVersion;
    }

    public Serializable getBody() {
        return body;
    }

    public void setBody(Serializable body) {
        this.body = body;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public EventoRequest setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
    }
}
