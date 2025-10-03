package com.evento.demo.api.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;
import com.evento.demo.api.view.enums.FailStage;


public class UtilEvent extends ServiceEvent {
    private int failEvent;
    private FailStage failStage;

    public UtilEvent() {
    }

    public UtilEvent(int failEvent, FailStage failStage) {
        this.failEvent = failEvent;
        this.failStage = failStage;
    }

    public int getFailEvent() {
        return failEvent;
    }

    public void setFailEvent(int failEvent) {
        this.failEvent = failEvent;
    }

    public FailStage getFailStage() {
        return failStage;
    }

    public void setFailStage(FailStage failStage) {
        this.failStage = failStage;
    }
}
