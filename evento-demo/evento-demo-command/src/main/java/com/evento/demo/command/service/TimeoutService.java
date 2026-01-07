package com.evento.demo.command.service;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.demo.api.command.TimeoutCommand;
import com.evento.demo.api.command.UtilCommand;
import com.evento.demo.api.command.UtilFailCommand;
import com.evento.demo.api.error.InvalidCommandException;
import com.evento.demo.api.event.UtilEvent;
import com.evento.demo.api.utils.Utils;
import com.evento.demo.api.view.enums.FailStage;

@Service
public class TimeoutService {

    @CommandHandler
    public void handle(TimeoutCommand command) {
        Utils.logMethodFlow(this, "handle", command, "BEGIN");
        Utils.doWork(command.getMillis(), command.getTimes());
        Utils.logMethodFlow(this, "handle", command, "END");
    }

}
