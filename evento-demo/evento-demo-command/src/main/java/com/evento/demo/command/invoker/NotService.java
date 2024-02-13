package com.evento.demo.command.invoker;

import com.evento.application.EventoBundle;
import org.springframework.stereotype.Component;

@Component
public class NotService {

    private final NotificationCommandInvoker invoker;

    public NotService(EventoBundle bundle) {
        this.invoker = bundle.getInvoker(NotificationCommandInvoker.class);
    }

    public void send(String body){
        invoker.send(body);
    }
}
