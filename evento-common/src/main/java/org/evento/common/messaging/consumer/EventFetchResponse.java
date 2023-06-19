package org.evento.common.messaging.consumer;

import org.evento.common.modeling.messaging.dto.PublishedEvent;

import java.io.Serializable;
import java.util.ArrayList;

public class EventFetchResponse implements Serializable {
	private ArrayList<PublishedEvent> events;

	public EventFetchResponse() {
	}

	public EventFetchResponse(ArrayList<PublishedEvent> events) {
		this.events = events;
	}

	public ArrayList<PublishedEvent> getEvents() {
		return events;
	}

	public void setEvents(ArrayList<PublishedEvent> events) {
		this.events = events;
	}
}
