package org.evento.common.modeling.messaging.message.internal;

import java.io.Serializable;

public class EventoResponse implements Serializable {

    private String correlationId;
    private Serializable body;

    public Serializable getBody() {
        return body;
    }

    public void setBody(Serializable body) {
        this.body = body;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
