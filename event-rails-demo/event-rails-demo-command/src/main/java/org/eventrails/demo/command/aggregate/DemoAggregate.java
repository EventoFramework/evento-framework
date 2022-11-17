package org.eventrails.demo.command.aggregate;

import jdk.jshell.execution.Util;
import org.eventrails.demo.api.command.DemoCreateCommand;
import org.eventrails.demo.api.command.DemoDeleteCommand;
import org.eventrails.demo.api.command.DemoUpdateCommand;
import org.eventrails.demo.api.event.DemoCreatedEvent;
import org.eventrails.demo.api.event.DemoDeletedEvent;
import org.eventrails.demo.api.event.DemoUpdatedEvent;
import org.eventrails.common.modeling.annotations.component.Aggregate;
import org.eventrails.common.modeling.annotations.handler.AggregateCommandHandler;
import org.eventrails.common.modeling.annotations.handler.EventSourcingHandler;
import org.eventrails.common.messaging.gateway.CommandGateway;
import org.eventrails.common.messaging.gateway.QueryGateway;
import org.eventrails.common.modeling.messaging.message.application.CommandMessage;
import org.eventrails.common.modeling.messaging.message.application.EventMessage;
import org.eventrails.demo.api.utils.Utils;

@Aggregate(snapshotFrequency=10)
public class DemoAggregate {

	@AggregateCommandHandler(init = true)
	DemoCreatedEvent handle(DemoCreateCommand command,
							DemoAggregateState state,
							CommandGateway commandGateway,
							QueryGateway queryGateway,
							CommandMessage commandMessage){
		Utils.logMethodFlow(this,"handle", command, "BEGIN");
		Utils.doWork(1200);
		Utils.logMethodFlow(this,"handle", command, "END");
		return new DemoCreatedEvent(
				command.getDemoId(),
				command.getName(),
				command.getValue());
	}

	@EventSourcingHandler
	DemoAggregateState on(DemoCreatedEvent event, DemoAggregateState state, EventMessage<DemoCreatedEvent> eventMessage){
		Utils.logMethodFlow(this,"on", event, "ES");
		return new DemoAggregateState(event.getValue());
	}

	@AggregateCommandHandler
	DemoUpdatedEvent handle(DemoUpdateCommand command,
							DemoAggregateState state){

		Utils.logMethodFlow(this,"handle", command, "BEGIN");
		Utils.doWork(1100);
		if(state.getValue() >= command.getValue()) throw new RuntimeException("error.invalid.value");
		Utils.logMethodFlow(this,"handle", command, "END");
		return new DemoUpdatedEvent(
				command.getDemoId(),
				command.getName(),
				command.getValue());
	}

	@EventSourcingHandler
	DemoAggregateState on(DemoUpdatedEvent event, DemoAggregateState state){
		Utils.logMethodFlow(this,"on", event, "ES");
		state.setValue(event.getValue());
		return state;
	}

	@AggregateCommandHandler
	DemoDeletedEvent handle(DemoDeleteCommand command,
							DemoAggregateState state){
		Utils.logMethodFlow(this,"handle", command, "BEGIN");
		Utils.doWork(900);
		Utils.logMethodFlow(this,"handle", command, "END");
		return new DemoDeletedEvent(
				command.getDemoId());
	}

	@EventSourcingHandler
	DemoAggregateState on(DemoDeletedEvent event, DemoAggregateState state){
		Utils.logMethodFlow(this,"on", event, "ES");
		state.setDeleted(true);
		return state;
	}

}
