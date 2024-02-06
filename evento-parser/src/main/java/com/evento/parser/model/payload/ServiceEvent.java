package com.evento.parser.model.payload;

/**
 * The ServiceEvent class represents a service event.
 * It extends the Event class and inherits its properties and methods.
 */
public class ServiceEvent extends Event {
	/**
	 * Creates a new ServiceEvent object with the specified name.
	 *
	 * @param name the name of the event
	 */
	public ServiceEvent(String name) {
		super(name);
	}

	/**
	 * The ServiceEvent class represents a service event.
	 * It extends the Event class and inherits its properties and methods.
	 */
	public ServiceEvent() {
		super();
	}

	@Override
	public String toString() {
		return getName();
	}
}
