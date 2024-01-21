package org.evento.parser.model.payload;

/**
 * The Event class represents an event payload.
 * It extends the Payload class and inherits its properties and methods.
 * <p>
 * Usage example:
 * Event event = new Event("eventName");
 */
public class Event extends Payload {
	/**
	 * Constructs a new Event object with the specified name.
	 *
	 * @param name the name of the event
	 */
	public Event(String name) {
		super(name);
	}

	/**
	 * Constructs a new Event object.
	 *
	 * @see Payload#Payload()
	 */
	public Event() {
		super();
	}
}
