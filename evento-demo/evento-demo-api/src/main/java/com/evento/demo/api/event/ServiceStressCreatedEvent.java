package com.evento.demo.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

import java.time.ZonedDateTime;

public class ServiceStressCreatedEvent extends ServiceEvent {

    private String stressIdentifier;
    private ZonedDateTime timestamp;

    public ServiceStressCreatedEvent(String stressIdentifier, long instance) {
        this.stressIdentifier = stressIdentifier;
        timestamp = ZonedDateTime.now();
    }

    public ServiceStressCreatedEvent() {}

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
