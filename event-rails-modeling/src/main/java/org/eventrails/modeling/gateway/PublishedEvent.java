package org.eventrails.modeling.gateway;


import java.io.Serializable;
import java.time.Instant;



public class PublishedEvent implements Serializable {
	private String eventId;
	private Long eventSequenceNumber;
	private Long aggregateSequenceNumber;
	private String aggregateId;
	private String eventMessage;
	private String eventName;
	private Instant createdAt;

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

	public String getEventMessage() {
		return eventMessage;
	}

	public void setEventMessage(String eventMessage) {
		this.eventMessage = eventMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public String getEventName() {
		return eventName;
	}

	public void setEventName(String eventName) {
		this.eventName = eventName;
	}
}