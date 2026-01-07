package com.evento.demo.command.invoker;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.demo.api.command.NotificationSendCommand;

import java.util.concurrent.ExecutionException;

@Invoker
public class NotificationCommandInvoker extends InvokerWrapper {

    @InvocationHandler
    public void send(String body) throws InterruptedException, ExecutionException {
        getCommandGateway().send(new NotificationSendCommand(body)).get();
    }
}
