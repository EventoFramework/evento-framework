package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.state.SerializedAggregateState;

import java.io.Serializable;
import java.util.Collection;

public class DecoratedDomainCommandMessage implements Serializable {
	private DomainCommandMessage commandMessage;
	private SerializedAggregateState<?> serializedAggregateState;
	private Collection<DomainEventMessage> eventStream;


	public DecoratedDomainCommandMessage() {
	}


	public SerializedAggregateState<?> getSerializedAggregateState() {
		return serializedAggregateState;
	}

	public void setSerializedAggregateState(SerializedAggregateState<?> serializedAggregateState) {
		this.serializedAggregateState = serializedAggregateState;
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
