package com.evento.demo.command.service;


import com.evento.common.messaging.gateway.QueryGateway;
import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.demo.api.command.ServiceStressCallCommand;
import com.evento.demo.api.command.ServiceStressCreateCommand;
import com.evento.demo.api.command.ServiceStressCreateCommand;
import com.evento.demo.api.event.ServiceStressCalledEvent;
import com.evento.demo.api.event.ServiceStressCreatedEvent;
import com.evento.demo.api.query.DemoViewFindAllQuery;
import com.evento.demo.api.utils.StressDB;

import java.time.ZonedDateTime;
import java.util.concurrent.ExecutionException;

@Service()
public class StressService {

    static final StressDB stressDB = new StressDB();
    static {
        StressService.stressDB.init();
    }

    @CommandHandler()
    public ServiceStressCreatedEvent handle(ServiceStressCreateCommand command){
        return new ServiceStressCreatedEvent(command.getStressIdentifier(), command.getInstances());
    }


    @CommandHandler
    public ServiceStressCalledEvent handle(ServiceStressCallCommand command,
                                           QueryGateway queryGateway) throws ExecutionException, InterruptedException {
        queryGateway.query(new DemoViewFindAllQuery(100,0)).get();
        stressDB.stressInstanceHandled(
                command.getStressIdentifier(),
                command.getInstance(),
                ZonedDateTime.now()
        );
        return new ServiceStressCalledEvent(command.getStressIdentifier(), command.getInstance());
    }




}
