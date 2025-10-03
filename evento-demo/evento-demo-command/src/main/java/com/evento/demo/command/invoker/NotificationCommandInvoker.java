package com.evento.demo.command.invoker;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.demo.api.command.NotificationSendCommand;

@Invoker
public class NotificationCommandInvoker extends InvokerWrapper {

    @InvocationHandler
    public void send(String body) throws InterruptedException {
        getCommandGateway().sendAndWait(new NotificationSendCommand(body));
    }
}
