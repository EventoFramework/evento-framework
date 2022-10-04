package org.eventrails.modeling.messaging.message;

import org.eventrails.modeling.messaging.payload.DomainCommand;

public class DomainCommandMessage extends CommandMessage<DomainCommand> {
	private String aggregateId;

	public DomainCommandMessage(DomainCommand command) {
		super(command);
		this.aggregateId = command.getAggregateId();
	}

	public DomainCommandMessage(){}

	public String getAggregateId() {
		return aggregateId;
	}

	public void setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
	}

	@Override
	public void setPayload(DomainCommand payload) {
		super.setPayload(payload);
		this.aggregateId = payload.getAggregateId();
	}
}
