package com.evento.lab.bundle.command;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.api.command.LabObserverFailCommand;
import com.evento.lab.api.command.LabSagaFailCommand;
import com.evento.lab.api.event.LabObserverFailEvent;
import com.evento.lab.api.event.LabSagaFailEvent;

@Service
public class LabFailScenarioService {

    @CommandHandler
    LabSagaFailEvent handle(LabSagaFailCommand cmd) {
        return new LabSagaFailEvent();
    }

    @CommandHandler
    LabObserverFailEvent handle(LabObserverFailCommand cmd) {
        return new LabObserverFailEvent();
    }
}
