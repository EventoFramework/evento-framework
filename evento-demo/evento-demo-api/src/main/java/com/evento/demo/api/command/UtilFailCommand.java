package com.evento.demo.api.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;
import com.evento.common.modeling.messaging.payload.ServiceEvent;
import com.evento.demo.api.view.enums.FailStage;


public class UtilFailCommand extends ServiceCommand {
    private FailStage failStage;

    public UtilFailCommand(FailStage failStage) {
        this.failStage = failStage;
    }

    public UtilFailCommand() {
    }

    public FailStage getFailStage() {
        return failStage;
    }

    public void setFailStage(FailStage failStage) {
        this.failStage = failStage;
    }
}
