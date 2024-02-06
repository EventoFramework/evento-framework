package com.evento.parser.model.handler;

import com.evento.parser.model.payload.DomainEvent;

/**
 * EventSourcingHandler is a class that represents a handler for domain events produced by an Aggregate.
 * It extends the Handler class and inherits its properties and methods.
 * <p>
 * Usage example:
 * EventSourcingHandler handler = new EventSourcingHandler(payload, line);
 */
public class EventSourcingHandler extends Handler<DomainEvent> {
	/**
	 * EventSourcingHandler is a class that represents a handler for domain events produced by an Aggregate.
	 * It extends the Handler class and inherits its properties and methods.
	 * <p>
	 * Usage example:
	 * EventSourcingHandler handler = new EventSourcingHandler(payload, line);
     * @param payload the Domain Event
     * @param line the file line
     */
	public EventSourcingHandler(DomainEvent payload, int line) {
		super(payload, line);
	}

	/**
	 * EventSourcingHandler is a class that represents a handler for domain events produced by an Aggregate.
	 * It extends the Handler class and inherits its properties and methods.
	 */
	public EventSourcingHandler() {
	}
}
