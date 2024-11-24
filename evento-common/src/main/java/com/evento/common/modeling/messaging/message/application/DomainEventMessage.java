package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.messaging.payload.DomainEvent;

/**
 * The DomainEventMessage class represents a specialized type of EventMessage that encapsulates a DomainEvent.
 * It inherits from the EventMessage class.
 *
 * @see EventMessage
 */
public class DomainEventMessage extends EventMessage<DomainEvent> {
	/**
	 * Constructs a new DomainEventMessage.
	 *
	 * @param payload The payload of the DomainEventMessage.
	 *                The payload must be an instance of DomainEvent or its subclass.
	 *
	 * @see DomainEvent
	 */
	public DomainEventMessage(DomainEvent payload) {
		super(payload);
	}

	/**
	 * Constructs a new DomainEventMessage.
	 * <p>
	 * This constructor is used to create an instance of DomainEventMessage without any parameters.
	 * <p>
	 * Example usage:
	 * DomainEventMessage eventMessage = new DomainEventMessage();
	 */
	public DomainEventMessage() {
	}

	@Override
	public String toString() {
		return "DomainEventMessage{} " + super.toString();
	}
}
