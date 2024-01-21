package org.evento.common.modeling.messaging.dto;


import org.evento.common.modeling.messaging.message.application.EventMessage;

import java.io.Serializable;
import java.sql.Timestamp;


/**
 * The PublishedEvent class represents a published event in the event sourcing architecture.
 * It contains information about the event sequence number, aggregate ID, event message, event name, and creation timestamp.
 */
public class PublishedEvent implements Serializable {
	private Long eventSequenceNumber;
	private String aggregateId;
	private EventMessage<?> eventMessage;
	private String eventName;
	private Timestamp createdAt;

	/**
	 * Retrieves the event sequence number of a PublishedEvent.
	 *
	 * @return the event sequence number of the PublishedEvent.
	 */
	public Long getEventSequenceNumber() {
		return eventSequenceNumber;
	}

	/**
	 * Sets the event sequence number for a PublishedEvent object.
	 *
	 * @param eventSequenceNumber the event sequence number to be set
	 */
	public void setEventSequenceNumber(Long eventSequenceNumber) {
		this.eventSequenceNumber = eventSequenceNumber;
	}

	/**
	 * Retrieves the aggregate ID of the PublishedEvent.
	 *
	 * @return the aggregate ID of the PublishedEvent as a String.
	 */
	public String getAggregateId() {
		return aggregateId;
	}

	/**
	 * Sets the aggregate ID of the PublishedEvent.
	 * <p>
	 * The aggregate ID is a unique identifier for an aggregate in the event sourcing architecture.
	 * It is typically a string value that uniquely identifies an aggregate instance.
	 * The aggregate ID can be set using the setAggregateId method and retrieved using the getAggregateId method.
	 *
	 * @param aggregateId the aggregate ID to be set as a String
	 *
	 * @see PublishedEvent#getAggregateId()
	 * @see PublishedEvent#setAggregateId(String)
	 */
	public void setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
	}

	/**
	 * Retrieves the event message of the PublishedEvent.
	 *
	 * @return the event message of the PublishedEvent.
	 */
	public EventMessage<?> getEventMessage() {
		return eventMessage;
	}

	/**
	 * Sets the event message of the PublishedEvent.
	 * <p>
	 * This method allows you to set the event message for a PublishedEvent object.
	 * The event message represents a message containing an event payload.
	 * It should be an instance of EventMessage or its subtypes.
	 * Once set, the event message can be retrieved using the getEventMessage method.
	 * <p>
	 * Usage example:
	 * <pre>{@code
	 * PublishedEvent event = new PublishedEvent();
	 * EventMessage<MyEventPayload> eventMessage = new EventMessage<>(new MyEventPayload());
	 * event.setEventMessage(eventMessage);
	 * }</pre>
	 *
	 * @param eventMessage the event message to be set for the PublishedEvent
	 * @see PublishedEvent#getEventMessage()
	 * @see EventMessage
	 */
	public void setEventMessage(EventMessage<?> eventMessage) {
		this.eventMessage = eventMessage;
	}

	/**
	 * Retrieves the creation timestamp of the PublishedEvent.
	 *
	 * @return the creation timestamp of the PublishedEvent as a Timestamp object
	 */
	public Timestamp getCreatedAt() {
		return createdAt;
	}

	/**
	 * Sets the creation timestamp of the PublishedEvent.
	 * <p>
	 * This method allows you to set the creation timestamp for a PublishedEvent object.
	 * The creation timestamp represents the time when the event was created.
	 * It should be an instance of Timestamp or its subclasses.
	 * Once set, the creation timestamp can be retrieved using the getCreatedAt method.
	 *
	 * @param createdAt the creation timestamp to be set for the PublishedEvent
	 * @see PublishedEvent#getCreatedAt()
	 */
	public void setCreatedAt(Timestamp createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Retrieves the event name of the PublishedEvent.
	 *
	 * @return the event name of the PublishedEvent as a String.
	 */
	public String getEventName() {
		return eventName;
	}

	/**
	 * Sets the event name of the PublishedEvent.
	 *
	 * @param eventName the event name to be set as a String
	 */
	public void setEventName(String eventName) {
		this.eventName = eventName;
	}
}