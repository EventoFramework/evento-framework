package org.eventrails.modeling.gateway;


import org.eventrails.modeling.messaging.message.EventMessage;

import java.io.Serializable;
import java.time.Instant;


public class PublishedEvent implements Serializable {
	private String eventId;
	private Long eventSequenceNumber;
	private Long aggregateSequenceNumber;
	private String aggregateId;
	private EventMessage<?> eventMessage;
	private String eventName;
	private Long createdAt;

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public Long getEventSequenceNumber() {
		return eventSequenceNumber;
	}

	public void setEventSequenceNumber(Long eventSequenceNumber) {
		this.eventSequenceNumber = eventSequenceNumber;
	}

	public Long getAggregateSequenceNumber() {
		return aggregateSequenceNumber;
	}

	public void setAggregateSequenceNumber(Long aggregateSequenceNumber) {
		this.aggregateSequenceNumber = aggregateSequenceNumber;
	}

	public String getAggregateId() {
		return aggregateId;
	}

	public void setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
	}

	public EventMessage<?> getEventMessage() {
		return eventMessage;
	}

	public void setEventMessage(EventMessage<?> eventMessage) {
		this.eventMessage = eventMessage;
	}

	public Long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Long createdAt) {
		this.createdAt = createdAt;
	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}
}