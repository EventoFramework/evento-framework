package org.evento.parser.model.handler;

import org.evento.parser.model.payload.DomainEvent;

public class EventSourcingHandler extends Handler<DomainEvent> {
	public EventSourcingHandler(DomainEvent payload, int line) {
		super(payload, line);
	}

	public EventSourcingHandler() {
	}
}
