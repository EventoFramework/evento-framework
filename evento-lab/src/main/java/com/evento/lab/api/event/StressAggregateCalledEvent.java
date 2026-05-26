package com.evento.lab.api.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class StressAggregateCalledEvent extends DomainEvent {

    private String stressId;
    private long instance;

    public StressAggregateCalledEvent() {}

    public StressAggregateCalledEvent(String stressId, long instance) {
        this.stressId = stressId;
        this.instance = instance;
    }

    public String getStressId() { return stressId; }
    public void setStressId(String stressId) { this.stressId = stressId; }
    public long getInstance() { return instance; }
    public void setInstance(long instance) { this.instance = instance; }
}
