package com.evento.demo.command.aggregate;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.common.modeling.messaging.message.application.CommandMessage;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.messaging.message.application.Metadata;
import com.evento.demo.api.command.DemoCreateCommand;
import com.evento.demo.api.command.DemoDeleteCommand;
import com.evento.demo.api.command.DemoUpdateCommand;
import com.evento.demo.api.command.NotificationSendCommand;
import com.evento.demo.api.event.DemoCreatedEvent;
import com.evento.demo.api.event.DemoDeletedEvent;
import com.evento.demo.api.event.DemoUpdatedEvent;
import com.evento.demo.api.utils.Utils;
import org.springframework.util.Assert;

import java.time.Instant;

@Aggregate(snapshotFrequency = 10)
public class DemoAggregate {

	@AggregateCommandHandler(init = true)
	DemoCreatedEvent handle(DemoCreateCommand command,
							DemoAggregateState state,
							CommandGateway commandGateway,
							CommandMessage<DemoCreateCommand> commandMessage,
							Metadata metadata,
							Instant instant) {
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
						  EventMessage<DemoCreatedEvent> eventMessage,
						  Metadata metadata,
						  Instant instant) {
		Utils.logMethodFlow(this, "on", event, "ES");
		return new DemoAggregateState(event.getValue());
	}

	@AggregateCommandHandler
	DemoUpdatedEvent handle(DemoUpdateCommand command,
							DemoAggregateState state) {

		System.out.println("Previous update: " + state.getUpdateCount());
		Utils.logMethodFlow(this, "handle", command, "BEGIN");
		Utils.doWork(1100);
        Utils.logMethodFlow(this, "handle", command, "END");
		return new DemoUpdatedEvent(
				command.getDemoId(),
				command.getName(),
				command.getValue());
	}

	@EventSourcingHandler
	void on(DemoUpdatedEvent event, DemoAggregateState state) {
		Utils.logMethodFlow(this, "on", event, "ES");
		state.setValue(event.getValue());
		state.setUpdateCount(state.getUpdateCount() + 1);
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
