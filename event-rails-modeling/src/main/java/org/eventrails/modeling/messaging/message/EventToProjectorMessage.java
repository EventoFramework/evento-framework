package org.eventrails.modeling.messaging.message;

import java.io.Serializable;

public class EventToProjectorMessage implements Serializable {

	private EventMessage<?> eventMessage;

	private String projectorName;


	public EventToProjectorMessage(EventMessage<?> eventMessage, String projectorName) {
		this.eventMessage = eventMessage;
		this.projectorName = projectorName;
	}

	public EventToProjectorMessage() {
	}

	public EventMessage<?> getEventMessage() {
		return eventMessage;
	}

	public void setEventMessage(EventMessage<?> eventMessage) {
		this.eventMessage = eventMessage;
	}

	public String getProjectorName() {
		return projectorName;
	}

	public void setProjectorName(String projectorName) {
		this.projectorName = projectorName;
	}
}
