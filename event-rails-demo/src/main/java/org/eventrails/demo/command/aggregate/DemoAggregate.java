package org.eventrails.demo.command.aggregate;

import org.eventrails.demo.api.command.DemoCreateCommand;
import org.eventrails.demo.api.command.DemoDeleteCommand;
import org.eventrails.demo.api.command.DemoUpdateCommand;
import org.eventrails.demo.api.event.DemoCreatedEvent;
import org.eventrails.demo.api.event.DemoDeletedEvent;
import org.eventrails.demo.api.event.DemoUpdatedEvent;
import org.eventrails.modeling.annotations.component.Aggregate;
import org.eventrails.modeling.annotations.handler.AggregateCommandHandler;
import org.eventrails.modeling.annotations.handler.EventSourcingHandler;
import org.eventrails.modeling.gateway.CommandGateway;
import org.eventrails.modeling.gateway.QueryGateway;
import org.eventrails.modeling.messaging.CommandMessage;
import org.eventrails.modeling.messaging.EventMessage;

@Aggregate(snapshotFrequency=1000)
public class DemoAggregate {

	@AggregateCommandHandler(init = true)
	DemoCreatedEvent handle(DemoCreateCommand command,
							DemoAggregateState state,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							CommandMessage commandMessage){
		return new DemoCreatedEvent(
				command.getDemoId(),
				command.getName(),
				command.getValue());
	}

	@EventSourcingHandler
	DemoAggregateState on(DemoCreatedEvent event, DemoAggregateState state, EventMessage eventMessage){
		return new DemoAggregateState(event.getValue());
	}

	@AggregateCommandHandler
	DemoUpdatedEvent handle(DemoUpdateCommand command,
							DemoAggregateState state){
		if(state.getValue() >= command.getValue()) throw new RuntimeException("error.invalid.value");
		return new DemoUpdatedEvent(
				command.getDemoId(),
				command.getName(),
				command.getValue());
	}

	@EventSourcingHandler
	DemoAggregateState on(DemoUpdatedEvent event, DemoAggregateState state){
		state.setValue(event.getValue());
		return state;
	}

	@AggregateCommandHandler
	DemoDeletedEvent handle(DemoDeleteCommand command,
							DemoAggregateState state){
		return new DemoDeletedEvent(
				command.getDemoId());
	}

	@EventSourcingHandler
	DemoAggregateState on(DemoDeletedEvent event, DemoAggregateState state){
		state.setDeleted(true);
		return state;
	}

}
