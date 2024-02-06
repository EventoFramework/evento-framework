package com.evento.common.messaging.consumer;

import com.evento.common.modeling.messaging.dto.PublishedEvent;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * EventFetchResponse is a class that represents a response containing a list of published events.
 * It implements the Serializable interface to allow the objects of this class to be serialized.
 */
public class EventFetchResponse implements Serializable {
	private ArrayList<PublishedEvent> events;

	/**
	 * EventFetchResponse is a constructor for creating an instance of the EventFetchResponse class.
	 * This class represents a response containing a list of published events.
	 * It implements the Serializable interface to allow the objects of this class to be serialized.
	 */
	public EventFetchResponse() {
	}

	/**
	 * EventFetchResponse is a class that represents a response containing a list of published events.
	 *
	 * @param events The list of published events.
	 */
	public EventFetchResponse(ArrayList<PublishedEvent> events) {
		this.events = events;
	}

	/**
	 * Retrieves a list of published events.
	 *
	 * @return The list of published events.
	 */
	public ArrayList<PublishedEvent> getEvents() {
		return events;
	}

	/**
	 * Sets the list of published events.
	 * <p>
	 * This method sets the list of published events in the EventFetchResponse class.
	 * The list of events is provided as an argument to the method and assigned to the "events" member variable.
	 *
	 * @param events The list of published events to be set.
	 */
	public void setEvents(ArrayList<PublishedEvent> events) {
		this.events = events;
	}
}
