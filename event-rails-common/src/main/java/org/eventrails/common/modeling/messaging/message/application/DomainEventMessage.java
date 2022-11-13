package org.eventrails.common.modeling.messaging.message.application;

import org.eventrails.common.modeling.messaging.payload.DomainEvent;

public class DomainEventMessage extends EventMessage<DomainEvent> {
	public DomainEventMessage(DomainEvent payload) {
		super(payload);
	}

	public DomainEventMessage() {
	}
}
