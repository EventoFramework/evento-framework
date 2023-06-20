package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.Event;

public abstract class EventMessage<T extends Event> extends Message<T> {
	public EventMessage(T payload) {
		super(payload);
	}

	public EventMessage() {
	}

	public String getEventName() {
		return getPayloadName();
	}

	public String getAssociationValue(String associationProperty) {
		return getSerializedPayload().getTree().get(1).get(associationProperty).textValue();
	}
}
