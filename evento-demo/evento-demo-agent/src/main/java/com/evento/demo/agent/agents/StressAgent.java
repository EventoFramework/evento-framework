package com.evento.demo.agent.agents;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.demo.api.command.AggregateStressCallCommand;
import com.evento.demo.api.command.AggregateStressCreateCommand;
import com.evento.demo.api.command.ServiceStressCallCommand;
import com.evento.demo.api.command.ServiceStressCreateCommand;
import com.evento.demo.api.utils.StressDB;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Invoker
public class StressAgent extends InvokerWrapper {


    @InvocationHandler
    public String createAggregate(long instances,
                                  StressDB stressDB) throws ExecutionException, InterruptedException {
        var identifier = UUID.randomUUID().toString();

        getCommandGateway().send(new AggregateStressCreateCommand(
                identifier, instances
        )).get();

        stressDB.createStress(identifier, instances);

        return identifier;

    }

    @InvocationHandler
    public CompletableFuture<?> callAggregate(String test, long instance,
                                              StressDB stressDB) throws ExecutionException, InterruptedException {

        return CompletableFuture.supplyAsync(() -> {
            stressDB.stressInstanceSent(test, instance, ZonedDateTime.now());
            return null;
        }).thenCompose(e -> getCommandGateway().send(new AggregateStressCallCommand(
                test, instance
        ))).thenApply(e -> {
            stressDB.stressInstanceReceived(test, instance, ZonedDateTime.now());
            return e;
        });

    }

    @InvocationHandler
    public String createService(long instances,
                                  StressDB stressDB) throws ExecutionException, InterruptedException {
        var identifier = UUID.randomUUID().toString();

        getCommandGateway().send(new ServiceStressCreateCommand(
                identifier, instances
        )).get();

        stressDB.createStress(identifier, instances);

        return identifier;

    }

    @InvocationHandler
    public CompletableFuture<?> callService(String test, long instance,
                                              StressDB stressDB) throws ExecutionException, InterruptedException {

        return CompletableFuture.supplyAsync(() -> {
            stressDB.stressInstanceSent(test, instance, ZonedDateTime.now());
            return null;
        }).thenCompose(e -> getCommandGateway().send(new ServiceStressCallCommand(
                test, instance
        ))).thenApply(e -> {
            stressDB.stressInstanceReceived(test, instance, ZonedDateTime.now());
            return e;
        });

    }
}
