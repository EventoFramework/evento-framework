package com.evento.lab.bundle.command;

import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.lab.api.command.StressServiceCallCommand;
import com.evento.lab.api.command.StressServiceCreateCommand;
import com.evento.lab.api.event.StressServiceCalledEvent;
import com.evento.lab.api.event.StressServiceCreatedEvent;
import com.evento.lab.api.query.ListOrdersQuery;

import java.util.concurrent.ExecutionException;

@Service
public class LabStressService {

    @CommandHandler
    public StressServiceCreatedEvent handle(StressServiceCreateCommand cmd) {
        return new StressServiceCreatedEvent(cmd.getStressId(), cmd.getInstances());
    }

    @CommandHandler
    public StressServiceCalledEvent handle(StressServiceCallCommand cmd,
                                           QueryGateway queryGateway)
            throws ExecutionException, InterruptedException {
        queryGateway.query(new ListOrdersQuery()).get();
        return new StressServiceCalledEvent(cmd.getStressId(), cmd.getInstance());
    }
}
