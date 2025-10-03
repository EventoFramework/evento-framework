package com.evento.demo.command.service;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.demo.api.command.UtilCommand;
import com.evento.demo.api.command.UtilFailCommand;
import com.evento.demo.api.error.InvalidCommandException;
import com.evento.demo.api.event.UtilEvent;
import com.evento.demo.api.view.enums.FailStage;

import java.util.Objects;

@Service
public class UtilService {

    @CommandHandler
    public void handle(UtilFailCommand command) {
        if(command.getFailStage() == FailStage.HANDLING){
            throw new InvalidCommandException("Failed during handling");
        }
        if(command.getFailStage() == FailStage.AFTER_HANDLING_EXCEPTION){
            throw new RuntimeException("Force Fail");
        }
    }

    @CommandHandler
    public UtilEvent handle(UtilCommand command) {
        return new UtilEvent(command.getFailEvent(), command.getFailStage());
    }

}
