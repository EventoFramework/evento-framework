package com.evento.demo.api.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

import java.time.ZonedDateTime;

public class AggregateStressCreatedEvent extends DomainEvent {

    private String stressIdentifier;
    private ZonedDateTime timestamp;

    public AggregateStressCreatedEvent(String stressIdentifier, long instance) {
        this.stressIdentifier = stressIdentifier;
        timestamp = ZonedDateTime.now();
    }

    public AggregateStressCreatedEvent() {}

    public String getIdentifier(){
        return stressIdentifier;
    }

    public String getStressIdentifier() {
        return stressIdentifier;
    }
    public void setStressIdentifier(String stressIdentifier) {
        this.stressIdentifier = stressIdentifier;
    }
    public void setInstance(long instance) {}

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
