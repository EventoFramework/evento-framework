package com.evento.parser.model.component;

import com.evento.parser.model.handler.EventHandler;

import java.util.List;

/**
 * The Observer class represents a component that observes events and handles them using event handlers.
 * It extends the Component class and includes a list of event handlers.
 */
public class Observer extends Component {
	private List<EventHandler> eventHandlers;

	/**
	 * Retrieves the list of event handlers for the Observer component.
	 *
	 * @return a List of EventHandler objects representing the event handlers associated with the Observer component
	 */
	public List<EventHandler> getEventHandlers() {
		return eventHandlers;
	}

	/**
	 * Sets the list of event handlers for the Observer component.
	 *
	 * @param eventHandlers a List of EventHandler objects representing the event handlers associated with the Observer component
	 */
	public void setEventHandlers(List<EventHandler> eventHandlers) {
		this.eventHandlers = eventHandlers;
	}
}
