package org.eventrails.parser.model.handler;

import org.eventrails.parser.model.payload.DomainCommand;
import org.eventrails.parser.model.payload.DomainEvent;

public class AggregateCommandHandler extends Handler<DomainCommand> {

	private final DomainEvent producedEvent;
	public AggregateCommandHandler(DomainCommand payload, DomainEvent producedEvent) {
		super(payload);
		this.producedEvent = producedEvent;
	}

	public DomainEvent getProducedEvent() {
		return producedEvent;
	}
}
