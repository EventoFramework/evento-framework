package com.evento.lab.bundle.command;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.api.command.LabUtilCommand;
import com.evento.lab.api.command.LabUtilFailCommand;
import com.evento.lab.api.event.LabUtilEvent;
import com.evento.lab.api.view.FailStage;

@Service
public class LabUtilService {

    @CommandHandler
    public LabUtilEvent handle(LabUtilCommand cmd) {
        return new LabUtilEvent(cmd.getFailEvent(), cmd.getFailStage());
    }

    @CommandHandler
    public void handle(LabUtilFailCommand cmd) {
        if (cmd.getFailStage() == FailStage.HANDLING) {
            throw new IllegalStateException("LabUtilFailCommand: forced failure at HANDLING stage");
        }
        if (cmd.getFailStage() == FailStage.AFTER_HANDLING_EXCEPTION) {
            throw new RuntimeException("LabUtilFailCommand: forced failure at AFTER_HANDLING_EXCEPTION stage");
        }
    }
}
