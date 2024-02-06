package com.evento.parser.model.payload;

/**
 * The DomainEvent class represents a domain-specific event payload.
 * It extends the Event class and inherits its properties and methods.
 * <p>
 * Usage example:
 * DomainEvent event = new DomainEvent("eventName");
 */
public class DomainEvent extends Event {
	/**
	 * The DomainEvent class represents a domain-specific event payload.
	 * It extends the Event class and inherits its properties and methods.
	 * <p>
	 * Usage example:
	 * DomainEvent event = new DomainEvent("eventName");
	 *
	 * @param name the name of the domain event
	 */
	public DomainEvent(String name) {
		super(name);
	}

	/**
	 * The DomainEvent class represents a domain-specific event payload.
	 * It extends the Event class and inherits its properties and methods.
	 */
	public DomainEvent() {
		super();
	}
}
