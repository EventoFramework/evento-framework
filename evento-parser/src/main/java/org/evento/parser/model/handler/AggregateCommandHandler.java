package org.evento.parser.model.handler;

import org.evento.parser.model.payload.DomainCommand;
import org.evento.parser.model.payload.DomainEvent;

public class AggregateCommandHandler extends Handler<DomainCommand> {

	private DomainEvent producedEvent;
	public AggregateCommandHandler(DomainCommand payload, DomainEvent producedEvent) {
		super(payload);
		this.producedEvent = producedEvent;
	}

	public AggregateCommandHandler() {
	}

	public DomainEvent getProducedEvent() {
		return producedEvent;
	}

	public void setProducedEvent(DomainEvent producedEvent) {
		this.producedEvent = producedEvent;
	}
}
