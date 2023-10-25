package org.evento.demo.command.aggregate;

import org.evento.common.messaging.gateway.CommandGateway;
import org.evento.common.modeling.annotations.component.Aggregate;
import org.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import org.evento.common.modeling.annotations.handler.EventSourcingHandler;
import org.evento.common.modeling.messaging.message.application.CommandMessage;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.demo.api.command.DemoCreateCommand;
import org.evento.demo.api.command.DemoDeleteCommand;
import org.evento.demo.api.command.DemoUpdateCommand;
import org.evento.demo.api.command.NotificationSendCommand;
import org.evento.demo.api.event.DemoCreatedEvent;
import org.evento.demo.api.event.DemoDeletedEvent;
import org.evento.demo.api.event.DemoUpdatedEvent;
import org.evento.demo.api.utils.Utils;
import org.springframework.util.Assert;

@Aggregate(snapshotFrequency = 10)
public class DemoAggregate {

	@AggregateCommandHandler(init = true)
	DemoCreatedEvent handle(DemoCreateCommand command,
							DemoAggregateState state,
							CommandGateway commandGateway,
							CommandMessage<DemoCreateCommand> commandMessage) {
		Utils.logMethodFlow(this, "handle", command, "BEGIN");
		commandGateway.sendAndWait(new NotificationSendCommand(command.getName()));
		Assert.isTrue(command.getDemoId() != null, "error.command.not.valid.id");
		Assert.isTrue(command.getName() != null, "error.command.not.valid.name");
		Assert.isTrue(command.getValue() >= 0, "error.command.not.valid.value");
		Utils.doWork(1200);
		Utils.logMethodFlow(this, "handle", command, "END");
		return new DemoCreatedEvent(
				command.getDemoId(),
				command.getName(),
				command.getValue());
	}

	@EventSourcingHandler
	DemoAggregateState on(DemoCreatedEvent event,
						  DemoAggregateState state,
						  EventMessage<DemoCreatedEvent> eventMessage) {
		Utils.logMethodFlow(this, "on", event, "ES");
		return new DemoAggregateState(event.getValue());
	}

	@AggregateCommandHandler
	DemoUpdatedEvent handle(DemoUpdateCommand command,
							DemoAggregateState state) {

		Utils.logMethodFlow(this, "handle", command, "BEGIN");
		Utils.doWork(1100);
		if (state.getValue() >= command.getValue())
			throw new RuntimeException("error.invalid.value");
		Utils.logMethodFlow(this, "handle", command, "END");
		return new DemoUpdatedEvent(
				command.getDemoId(),
				command.getName(),
				command.getValue());
	}

	@EventSourcingHandler
	DemoAggregateState on(DemoUpdatedEvent event, DemoAggregateState state) {
		Utils.logMethodFlow(this, "on", event, "ES");
		state.setValue(event.getValue());
		return state;
	}

	@AggregateCommandHandler
	DemoDeletedEvent handle(DemoDeleteCommand command,
							DemoAggregateState state) {
		Utils.logMethodFlow(this, "handle", command, "BEGIN");
		Utils.doWork(900);
		Utils.logMethodFlow(this, "handle", command, "END");
		return new DemoDeletedEvent(
				command.getDemoId());
	}

	@EventSourcingHandler
	DemoAggregateState on(DemoDeletedEvent event, DemoAggregateState state) {
		Utils.logMethodFlow(this, "on", event, "ES");
		state.setDeleted(true);
		return state;
	}

}
