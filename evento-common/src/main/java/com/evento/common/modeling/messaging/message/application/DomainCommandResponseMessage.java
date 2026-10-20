package com.evento.common.modeling.messaging.message.application;

import com.evento.common.modeling.state.AggregateState;
import com.evento.common.modeling.state.SerializedAggregateState;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The DomainCommandResponseMessage class represents a response message for a domain command.
 * It contains information about the domain event message, serialized aggregate state, and whether the aggregate is deleted.
 */
public class DomainCommandResponseMessage implements Serializable {

	private DomainEventMessage domainEventMessage;
	private SerializedAggregateState<AggregateState> serializedAggregateState;

	private boolean aggregateDeleted;

	/**
	 * The events the command produced before {@link #domainEventMessage}, in order, when it
	 * produced several (3.0, {@code DomainEvents}); null when it produced one. Only sent to a
	 * broker that negotiated {@code events:1}: an older one would ignore the field and store the
	 * last event alone.
	 */
	private List<DomainEventMessage> precedingEventMessages;

	/**
	 * The DomainCommandResponseMessage class represents a response message for a domain command.
	 * It contains information about the domain event message, serialized aggregate state, and whether the aggregate is deleted.
	 */
	public DomainCommandResponseMessage() {

	}

	/**
	 * Constructs a DomainCommandResponseMessage.
	 *
	 * @param domainEventMessage         The domain event message.
	 * @param serializedAggregateState   The serialized aggregate state.
	 * @param aggregateDeleted           A flag indicating whether the aggregate is deleted.
	 */
	public DomainCommandResponseMessage(
			DomainEventMessage domainEventMessage,
			SerializedAggregateState<AggregateState> serializedAggregateState,
			boolean aggregateDeleted) {
		this.domainEventMessage = domainEventMessage;
		this.serializedAggregateState = serializedAggregateState;
		this.aggregateDeleted = aggregateDeleted;
	}

	/**
	 * Retrieves the domain event message contained in the DomainCommandResponseMessage.
	 *
	 * @return The domain event message.
	 */
	public DomainEventMessage getDomainEventMessage() {
		return domainEventMessage;
	}

	/**
	 * Sets the domain event message for the DomainCommandResponseMessage.
	 *
	 * @param domainEventMessage The domain event message to set.
	 */
	public void setDomainEventMessage(DomainEventMessage domainEventMessage) {
		this.domainEventMessage = domainEventMessage;
	}

	/**
	 * Retrieves the serialized aggregate state contained in the DomainCommandResponseMessage.
	 *
	 * @return The serialized aggregate state.
	 */
	public SerializedAggregateState<AggregateState> getSerializedAggregateState() {
		return serializedAggregateState;
	}

	/**
	 * Sets the serialized aggregate state for the DomainCommandResponseMessage.
	 *
	 * @param serializedAggregateState The serialized aggregate state to set.
	 *                                The type parameter T must extend the AggregateState class.
	 */
	public void setSerializedAggregateState(SerializedAggregateState<AggregateState> serializedAggregateState) {
		this.serializedAggregateState = serializedAggregateState;
	}

	/**
	 * Retrieves whether the aggregate is deleted.
	 *
	 * @return true if the aggregate is deleted, false otherwise.
	 */
	/** The events before the last one, or null when the command produced one. */
	public List<DomainEventMessage> getPrecedingEventMessages() {
		return precedingEventMessages;
	}

	public void setPrecedingEventMessages(List<DomainEventMessage> precedingEventMessages) {
		this.precedingEventMessages = precedingEventMessages == null || precedingEventMessages.isEmpty()
				? null : new ArrayList<>(precedingEventMessages);
	}

	/** Every event the command produced, in order: the preceding ones, then {@link #getDomainEventMessage()}. */
	public List<DomainEventMessage> allEventMessages() {
		if (precedingEventMessages == null) {
			return domainEventMessage == null ? List.of() : List.of(domainEventMessage);
		}
		var all = new ArrayList<>(precedingEventMessages);
		all.add(domainEventMessage);
		return all;
	}

	public boolean isAggregateDeleted() {
		return aggregateDeleted;
	}

	/**
	 * Sets the flag indicating whether the aggregate is deleted.
	 *
	 * @param aggregateDeleted the flag indicating whether the aggregate is deleted
	 */
	public void setAggregateDeleted(boolean aggregateDeleted) {
		this.aggregateDeleted = aggregateDeleted;
	}

	@Override
	public String toString() {
		return "DomainCommandResponseMessage{" +
				"domainEventMessage=" + domainEventMessage +
				", serializedAggregateState=" + serializedAggregateState +
				", aggregateDeleted=" + aggregateDeleted +
				(precedingEventMessages == null ? "" : ", precedingEventMessages=" + precedingEventMessages) +
				'}';
	}
}
