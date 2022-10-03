package org.eventrails.modeling.messaging.invocation;

import org.eventrails.modeling.messaging.message.CommandMessage;
import org.eventrails.modeling.messaging.message.DomainCommandMessage;
import org.eventrails.modeling.messaging.message.DomainEventMessage;
import org.eventrails.modeling.messaging.message.EventMessage;
import org.eventrails.modeling.messaging.payload.DomainCommand;
import org.eventrails.modeling.messaging.payload.DomainEvent;
import org.eventrails.modeling.state.AggregateState;

import java.util.Collection;

public class AggregateCommandHandlerInvocation {
	private DomainCommandMessage commandMessage;
	private AggregateState aggregateState;
	private Collection<DomainEventMessage> eventStream;

	public AggregateCommandHandlerInvocation(DomainCommandMessage commandMessage, AggregateState aggregateState, Collection<DomainEventMessage> eventStream) {
		this.commandMessage = commandMessage;
		this.aggregateState = aggregateState;
		this.eventStream = eventStream;
	}

	public AggregateCommandHandlerInvocation() {
	}




	public AggregateState getAggregateState() {
		return aggregateState;
	}

	public void setAggregateState(AggregateState aggregateState) {
		this.aggregateState = aggregateState;
	}


	public Collection<DomainEventMessage> getEventStream() {
		return eventStream;
	}

	public void setEventStream(Collection<DomainEventMessage> eventStream) {
		this.eventStream = eventStream;
	}

	public DomainCommandMessage getCommandMessage() {
		return commandMessage;
	}

	public void setCommandMessage(DomainCommandMessage commandMessage) {
		this.commandMessage = commandMessage;
	}
}
