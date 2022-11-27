package org.evento.common.messaging.consumer;

import java.io.Serializable;

public class EventFetchRequest implements Serializable {
    private long lastSequenceNumber;
    private int limit;

    public EventFetchRequest() {
    }

    public EventFetchRequest(long lastSequenceNumber, int limit) {
        this.lastSequenceNumber = lastSequenceNumber;
        this.limit = limit;
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
}
