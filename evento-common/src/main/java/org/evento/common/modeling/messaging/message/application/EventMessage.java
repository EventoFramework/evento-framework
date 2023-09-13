package org.evento.common.modeling.messaging.message.application;

import org.evento.common.modeling.messaging.payload.Event;
import org.evento.common.utils.Context;

public abstract class EventMessage<T extends Event> extends Message<T> {

	private String context;
	public EventMessage(T payload) {
		super(payload);
		this.context = payload == null ? Context.DEFAULT : payload.getContext();
	}

	public EventMessage() {
	}

	public String getEventName() {
		return getPayloadName();
	}

	public String getAssociationValue(String associationProperty) {
		return getSerializedPayload().getTree().get(1).get(associationProperty).textValue();
	}

	public String getContext() {
		return context;
	}

	public void setContext(String context) {
		this.context = context;
	}
}
