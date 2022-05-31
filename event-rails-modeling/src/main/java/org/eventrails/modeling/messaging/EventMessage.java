package org.eventrails.modeling.messaging;

import org.eventrails.modeling.messaging.payload.Event;

public abstract class EventMessage<T extends Event> extends Message<T> {
	public EventMessage(T payload) {
		super(payload);
	}
}
