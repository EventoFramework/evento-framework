package com.evento.demo.command.aggregate;


import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.demo.api.command.AggregateStressCallCommand;
import com.evento.demo.api.command.AggregateStressCreateCommand;
import com.evento.demo.api.event.AggregateStressCalledEvent;
import com.evento.demo.api.event.AggregateStressCreatedEvent;
import com.evento.demo.api.utils.StressDB;

import java.time.ZonedDateTime;

@Aggregate()
public class StressAggregate {

    static final StressDB stressDB = new StressDB();
    static {
        StressAggregate.stressDB.init();
    }

    @AggregateCommandHandler(init = true)
    public AggregateStressCreatedEvent handle(AggregateStressCreateCommand command){
        return new AggregateStressCreatedEvent(command.getStressIdentifier(), command.getInstances());
    }

    @EventSourcingHandler
    public StressAggregateState on(AggregateStressCreatedEvent event ){
        return new StressAggregateState();
    }

    @AggregateCommandHandler
    public AggregateStressCalledEvent handle(AggregateStressCallCommand command,
                                             StressAggregateState state){
        stressDB.stressInstanceHandled(
                command.getStressIdentifier(),
                command.getInstance(),
                ZonedDateTime.now()
        );
        return new AggregateStressCalledEvent(command.getStressIdentifier(), command.getInstance());
    }

    @EventSourcingHandler
    public void on(AggregateStressCalledEvent event, StressAggregateState state ){
        state.getInstances().add(event.getInstance());
    }


}
