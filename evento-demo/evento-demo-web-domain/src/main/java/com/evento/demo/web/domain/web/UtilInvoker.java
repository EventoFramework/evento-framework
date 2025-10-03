package com.evento.demo.web.domain.web;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.demo.api.command.UtilCommand;
import com.evento.demo.api.command.UtilFailCommand;
import com.evento.demo.api.error.InvalidCommandException;
import com.evento.demo.api.view.enums.FailStage;

import java.util.concurrent.CompletableFuture;

@Invoker
public class UtilInvoker extends InvokerWrapper {


    @InvocationHandler
    public CompletableFuture<Object> failSend(FailStage failStage) {
        if(failStage == FailStage.INVOKER){
            throw new InvalidCommandException("Failed in Invoker");
        }
        return getCommandGateway().send(new UtilFailCommand(failStage));
    }

    @InvocationHandler
    public void failSendAndWait(FailStage failStage) throws InterruptedException {
        if(failStage == FailStage.INVOKER){
            throw new InvalidCommandException("Failed in Invoker");
        }
       getCommandGateway().sendAndWait(new UtilFailCommand(failStage));
    }

    @InvocationHandler
    public CompletableFuture<?> failEventSend(int failures, FailStage failStage) {
        return getCommandGateway().send(new UtilCommand(failures, failStage));
    }
}
