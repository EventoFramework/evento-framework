package org.eventrails.server.es.eventstore;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(indexes = {
		@Index(name="aggregate_index", columnList = "aggregateId"),
		@Index(name="event_sequence_index", columnList = "eventSequenceNumber"),
		@Index(name="aggregate_sequence_index", columnList = "aggregateSequenceNumber")
})
public class EventStoreEntry {
	@Id
	private String eventId;

	private Long eventSequenceNumber;

	private Long aggregateSequenceNumber;
	private String aggregateId;
	@Column(columnDefinition = "JSON")
	private String eventMessage;
	private Instant createdAt;

	public EventStoreEntry(String eventId, Long eventSequenceNumber, Long aggregateSequenceNumber, String aggregateId, String eventMessage, Instant createdAt) {
		this.eventId = eventId;
		this.eventSequenceNumber = eventSequenceNumber;
		this.aggregateSequenceNumber = aggregateSequenceNumber;
		this.aggregateId = aggregateId;
		this.eventMessage = eventMessage;
		this.createdAt = createdAt;
	}

	public EventStoreEntry() {
	}

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

	public Long getAggregateSequenceNumber() {
		return aggregateSequenceNumber;
	}

	public void setAggregateSequenceNumber(Long aggregateSequenceNumber) {
		this.aggregateSequenceNumber = aggregateSequenceNumber;
	}
}
