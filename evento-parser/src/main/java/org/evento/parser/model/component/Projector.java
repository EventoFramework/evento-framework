package org.evento.parser.model.component;

import org.evento.parser.model.handler.EventHandler;

import java.util.List;

/**
 * The {@code Projector} class represents a projector component.
 * It extends the {@code Component} class and provides a list of event handlers.
 */
public class Projector extends Component {
	private List<EventHandler> eventHandlers;

	/**
	 * Returns the list of event handlers associated with the Projector component.
	 *
	 * @return a List of EventHandler objects representing the event handlers.
	 */
	public List<EventHandler> getEventHandlers() {
		return eventHandlers;
	}

	/**
	 * Sets the event handlers for the Projector component.
	 *
	 * @param eventHandlers the List of EventHandler objects representing the event handlers
	 */
	public void setEventHandlers(List<EventHandler> eventHandlers) {
		this.eventHandlers = eventHandlers;
	}
}
