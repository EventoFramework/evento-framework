package org.evento.server.es.snapshot;

import org.evento.common.modeling.state.SerializedAggregateState;
import org.evento.server.config.JsonConverter;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "es__snapshot", indexes = {
		@Index(name = "aggregate_index", columnList = "aggregateId"),
		@Index(name = "event_sequence_number_index", columnList = "eventSequenceNumber"),
})
public class Snapshot {
	@Id
	public String aggregateId;
	public Long eventSequenceNumber;
	@Column(columnDefinition = "BLOB")
	@Convert( converter = JsonConverter.class)
	public SerializedAggregateState<?> aggregateState;

	public Instant updatedAt;

	public String getAggregateId() {
		return aggregateId;
	}

	public void setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
	}

	public Long getEventSequenceNumber() {
		return eventSequenceNumber;
	}

	public void setEventSequenceNumber(Long eventSequenceNumber) {
		this.eventSequenceNumber = eventSequenceNumber;
	}

	public SerializedAggregateState<?> getAggregateState() {
		return aggregateState;
	}

	public void setAggregateState(SerializedAggregateState<?> aggregateState) {
		this.aggregateState = aggregateState;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
