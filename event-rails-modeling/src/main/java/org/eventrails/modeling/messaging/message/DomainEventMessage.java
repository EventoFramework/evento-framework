package org.eventrails.modeling.messaging.message;

import org.eventrails.modeling.messaging.payload.DomainEvent;

public class DomainEventMessage extends EventMessage<DomainEvent> {
	public DomainEventMessage(DomainEvent payload) {
		super(payload);
	}

	public DomainEventMessage() {
	}
}
