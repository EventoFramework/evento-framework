package org.evento.common.messaging.consumer;

import java.io.Serializable;

public class EventFetchRequest implements Serializable {
    private long lastSequenceNumber;
    private int limit;

    private String componentName;

    public EventFetchRequest() {
    }

    public EventFetchRequest(long lastSequenceNumber, int limit, String componentName) {
        this.lastSequenceNumber = lastSequenceNumber;
        this.limit = limit;
        this.componentName = componentName;
    }

    public long getLastSequenceNumber() {
        return lastSequenceNumber;
    }

    public void setLastSequenceNumber(long lastSequenceNumber) {
        this.lastSequenceNumber = lastSequenceNumber;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }
}
