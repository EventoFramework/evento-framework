package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.state.AggregateState;
import org.evento.common.modeling.state.SerializedAggregateState;

import java.io.Serializable;

public class DomainCommandResponseMessage implements Serializable {

	private DomainEventMessage domainEventMessage;
	private SerializedAggregateState<AggregateState> serializedAggregateState;

	private boolean aggregateDeleted;

	public DomainCommandResponseMessage() {

	}

	public DomainCommandResponseMessage(
			DomainEventMessage domainEventMessage,
			SerializedAggregateState<AggregateState> serializedAggregateState,
			boolean aggregateDeleted) {
		this.domainEventMessage = domainEventMessage;
		this.serializedAggregateState = serializedAggregateState;
		this.aggregateDeleted = aggregateDeleted;
	}

	public DomainEventMessage getDomainEventMessage() {
		return domainEventMessage;
	}

	public void setDomainEventMessage(DomainEventMessage domainEventMessage) {
		this.domainEventMessage = domainEventMessage;
	}

	public SerializedAggregateState<AggregateState> getSerializedAggregateState() {
		return serializedAggregateState;
	}

	public void setSerializedAggregateState(SerializedAggregateState<AggregateState> serializedAggregateState) {
		this.serializedAggregateState = serializedAggregateState;
	}

	public boolean isAggregateDeleted() {
		return aggregateDeleted;
	}

	public void setAggregateDeleted(boolean aggregateDeleted) {
		this.aggregateDeleted = aggregateDeleted;
	}
}
