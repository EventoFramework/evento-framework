package com.evento.lab.bundle.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.lab.api.command.StressAggregateCallCommand;
import com.evento.lab.api.command.StressAggregateCreateCommand;
import com.evento.lab.api.event.StressAggregateCalledEvent;
import com.evento.lab.api.event.StressAggregateCreatedEvent;

@Aggregate
public class LabStressAggregate {

    @AggregateCommandHandler(init = true)
    StressAggregateCreatedEvent handle(StressAggregateCreateCommand cmd) {
        return new StressAggregateCreatedEvent(cmd.getStressId(), cmd.getInstances());
    }

    @EventSourcingHandler
    LabStressAggregateState on(StressAggregateCreatedEvent e) {
        return new LabStressAggregateState();
    }

    @AggregateCommandHandler
    StressAggregateCalledEvent handle(StressAggregateCallCommand cmd, LabStressAggregateState state) {
        return new StressAggregateCalledEvent(cmd.getStressId(), cmd.getInstance());
    }

    @EventSourcingHandler
    void on(StressAggregateCalledEvent e, LabStressAggregateState state) {
        state.getInstances().add(e.getInstance());
    }
}
