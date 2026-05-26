package com.evento.lab.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.lab.api.view.FailStage;

public class LabUtilFailCommand extends ServiceCommand {

    private FailStage failStage;

    public LabUtilFailCommand() {}

    public LabUtilFailCommand(FailStage failStage) {
        this.failStage = failStage;
    }

    public FailStage getFailStage() { return failStage; }
    public void setFailStage(FailStage failStage) { this.failStage = failStage; }
}
