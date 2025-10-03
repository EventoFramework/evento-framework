package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.demo.api.view.enums.FailStage;


public class UtilCommand extends ServiceCommand {
    private int failEvent;
    private FailStage failStage;

    public UtilCommand() {
    }

    public UtilCommand(int failEvent, FailStage failStage) {
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
