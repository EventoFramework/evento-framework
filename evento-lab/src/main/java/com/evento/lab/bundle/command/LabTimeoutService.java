package com.evento.lab.bundle.command;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.api.command.LabTimeoutCommand;

@Service
public class LabTimeoutService {

    @CommandHandler
    public void handle(LabTimeoutCommand cmd) {
        for (int i = 0; i < cmd.getTimes(); i++) {
            try {
                Thread.sleep(cmd.getMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
