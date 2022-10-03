package org.eventrails.modeling.messaging.invocation;

import org.eventrails.modeling.messaging.message.EventMessage;

public class ProjectorEventHandlerInvocation {
	private EventMessage<?> eventMessage;

	public ProjectorEventHandlerInvocation(EventMessage<?> eventMessage) {
		this.eventMessage = eventMessage;
	}

	public ProjectorEventHandlerInvocation() {
	}

	public EventMessage<?> getEventMessage() {
		return eventMessage;
	}

	public void setEventMessage(EventMessage<?> eventMessage) {
		this.eventMessage = eventMessage;
	}
}
