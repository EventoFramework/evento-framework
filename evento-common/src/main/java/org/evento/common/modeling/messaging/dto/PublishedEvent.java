package org.evento.common.modeling.messaging.dto;


import org.evento.common.modeling.messaging.message.application.EventMessage;

import java.io.Serializable;
import java.sql.Timestamp;


public class PublishedEvent implements Serializable {
	private Long eventSequenceNumber;
	private String aggregateId;
	private EventMessage<?> eventMessage;
	private String eventName;
	private Timestamp createdAt;

	public Long getEventSequenceNumber() {
		return eventSequenceNumber;
	}

	public void setEventSequenceNumber(Long eventSequenceNumber) {
		this.eventSequenceNumber = eventSequenceNumber;
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

	public Timestamp getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Timestamp createdAt) {
		this.createdAt = createdAt;
	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}
}