package com.evento.lab.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.lab.api.view.FailStage;

public class LabUtilCommand extends ServiceCommand {

    private int failEvent;
    private FailStage failStage;

    public LabUtilCommand() {}

    public LabUtilCommand(int failEvent, FailStage failStage) {
        this.failEvent = failEvent;
        this.failStage = failStage;
    }

    public int getFailEvent() { return failEvent; }
    public void setFailEvent(int failEvent) { this.failEvent = failEvent; }
    public FailStage getFailStage() { return failStage; }
    public void setFailStage(FailStage failStage) { this.failStage = failStage; }
}
