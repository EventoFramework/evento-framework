package com.evento.demo.api.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

import java.time.ZonedDateTime;

public class AggregateStressCalledEvent extends DomainEvent {

    private String stressIdentifier;
    private long instance;
    private ZonedDateTime timestamp;

    public AggregateStressCalledEvent(String stressIdentifier, long instance) {
        this.stressIdentifier = stressIdentifier;
        this.instance = instance;
        timestamp = ZonedDateTime.now();
    }

    public AggregateStressCalledEvent() {}

    public String getIdentifier(){
        return stressIdentifier + '_' + instance;
    }

    public String getStressIdentifier() {
        return stressIdentifier;
    }
    public void setStressIdentifier(String stressIdentifier) {
        this.stressIdentifier = stressIdentifier;
    }
    public long getInstance() {
        return instance;
    }
    public void setInstance(long instance) {}

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
