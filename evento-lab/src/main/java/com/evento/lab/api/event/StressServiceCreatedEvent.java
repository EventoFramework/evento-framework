package com.evento.lab.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class StressServiceCreatedEvent extends ServiceEvent {

    private String stressId;
    private long instances;

    public StressServiceCreatedEvent() {}

    public StressServiceCreatedEvent(String stressId, long instances) {
        this.stressId = stressId;
        this.instances = instances;
    }

    public String getStressId() { return stressId; }
    public void setStressId(String stressId) { this.stressId = stressId; }
    public long getInstances() { return instances; }
    public void setInstances(long instances) { this.instances = instances; }
}
