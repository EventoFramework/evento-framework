package com.evento.lab.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class StressServiceCalledEvent extends ServiceEvent {

    private String stressId;
    private long instance;

    public StressServiceCalledEvent() {}

    public StressServiceCalledEvent(String stressId, long instance) {
        this.stressId = stressId;
        this.instance = instance;
    }

    public String getStressId() { return stressId; }
    public void setStressId(String stressId) { this.stressId = stressId; }
    public long getInstance() { return instance; }
    public void setInstance(long instance) { this.instance = instance; }
}
