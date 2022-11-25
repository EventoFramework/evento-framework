package org.eventrails.common.messaging.consumer;

import java.io.Serializable;

public class EventLastSequenceNumberResponse implements Serializable {
    private long number;

    public EventLastSequenceNumberResponse() {
    }

    public EventLastSequenceNumberResponse(long number) {
        this.number = number;
    }

    public long getNumber() {
        return number;
    }

    public void setNumber(long number) {
        this.number = number;
    }
}
