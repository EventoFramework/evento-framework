package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.state.SerializedAggregateState;

import java.io.Serializable;
import java.util.Collection;

/**
 * Represents a decorated domain command message.
 * The DecoratedDomainCommandMessage class is used to encapsulate a DomainCommandMessage along with additional information for processing.
 * It contains the command message, the serialized aggregate state, and the event stream associated with the command.
 */
public class DecoratedDomainCommandMessage implements Serializable {
	private DomainCommandMessage commandMessage;
	private SerializedAggregateState<?> serializedAggregateState;
	private Collection<DomainEventMessage> eventStream;


	/**
	 * Represents a decorated domain command message.
	 * The DecoratedDomainCommandMessage class is used to encapsulate a DomainCommandMessage along with additional information for processing.
	 * It contains the command message, the serialized aggregate state, and the event stream associated with the command.
	 */
	public DecoratedDomainCommandMessage() {
	}


	/**
	 * Retrieves the serialized aggregate state.
	 *
	 * @return The serialized aggregate state.
	 */
	public SerializedAggregateState<?> getSerializedAggregateState() {
		return serializedAggregateState;
	}

	/**
	 * Sets the serialized aggregate state.
	 *
	 * @param serializedAggregateState The serialized aggregate state to set.
	 */
	public void setSerializedAggregateState(SerializedAggregateState<?> serializedAggregateState) {
		this.serializedAggregateState = serializedAggregateState;
	}


	/**
	 * Retrieves the event stream associated with the decorated domain command message.
	 *
	 * @return The event stream.
	 */
	public Collection<DomainEventMessage> getEventStream() {
		return eventStream;
	}

	/**
	 * Sets the event stream associated with the decorated domain command message.
	 *
	 * @param eventStream The event stream to set.
	 */
	public void setEventStream(Collection<DomainEventMessage> eventStream) {
		this.eventStream = eventStream;
	}

	/**
	 * Retrieves the command message associated with this DecoratedDomainCommandMessage.
	 *
	 * @return The command message.
	 */
	public DomainCommandMessage getCommandMessage() {
		return commandMessage;
	}

	/**
	 * Sets the command message for the DecoratedDomainCommandMessage.
	 *
	 * @param commandMessage The command message to set.
	 */
	public void setCommandMessage(DomainCommandMessage commandMessage) {
		this.commandMessage = commandMessage;
	}
}
