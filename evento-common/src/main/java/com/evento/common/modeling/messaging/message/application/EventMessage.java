package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.messaging.payload.Event;
import com.evento.common.utils.Context;

/**
 * EventMessage is an abstract class that represents a message containing an event payload.
 *
 * @param <T> The type of the event payload.
 */
public abstract class EventMessage<T extends Event> extends Message<T> {

	private String context;
	/**
	 * Constructs a new EventMessage with the given payload.
	 *
	 * @param payload the payload of the EventMessage
	 *
     */
	public EventMessage(T payload) {
		super(payload);
		this.context = payload == null ? Context.DEFAULT : payload.getContext();
	}

	/**
	 * EventMessage is a class that represents a message containing an event payload.
	 *
     */
	public EventMessage() {
	}

	/**
	 * Returns the name of the event.
	 *
	 * @return the name of the event
	 */
	public String getEventName() {
		return getPayloadName();
	}

	/**
	 * Retrieves the value of the association property from the serialized payload used by saga components.
	 *
	 * @param associationProperty the property key of the association
	 * @return the value of the association property as a string
	 */
	public String getAssociationValue(String associationProperty) {
		return getSerializedPayload().getTree().get(1).get(associationProperty).textValue();
	}

	/**
	 * Retrieves the context of the EventMessage.
	 * <p>
	 * The context is a string value representing the available context options for certain functionalities within a software system.
	 * It is set by calling the setContext method of the Event object.
	 * The context can be accessed using the getContext method of the Event object.
	 *
	 * @return the context of the EventMessage as a string
	 *
	 * @see Event#setContext(String)
	 * @see Event#getContext()
	 * @see EventMessage#setContext(String)
     */
	public String getContext() {
		return context;
	}

	/**
	 * Sets the context of the EventMessage.
	 * <p>
	 * The context is a string value representing the available context options for certain functionalities within a software system.
	 * It is set by calling the setContext method of the Event object.
	 *
	 * @param context the context to be set as a string
	 *
	 * @see Event#setContext(String)
	 * @see EventMessage#getContext()
	 */
	public void setContext(String context) {
		this.context = context;
	}

	@Override
	public String toString() {
		return "EventMessage{" +
				"context='" + context + '\'' +
				"} " + super.toString();
	}
}
