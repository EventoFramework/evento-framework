package org.eventrails.parser.model.handler;

import org.eventrails.parser.model.payload.DomainEvent;

public class EventSourcingHandler extends Handler<DomainEvent> {
	public EventSourcingHandler(DomainEvent payload) {
		super(payload);
	}
}
