package org.eventrails.server.es.snapshot;

import org.eventrails.common.modeling.state.SerializedAggregateState;
import org.eventrails.server.config.JsonConverter;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "es__snapshot", indexes = {
		@Index(name = "aggregate_index", columnList = "aggregateId"),
		@Index(name = "aggregate_sequence_index", columnList = "aggregateSequenceNumber")
})
public class Snapshot {
	@Id
	public String aggregateId;
	public Long aggregateSequenceNumber;
	@Column(columnDefinition = "JSON")
	@Convert( converter = JsonConverter.class)
	public SerializedAggregateState<?> aggregateState;

	public Instant updatedAt;

	public String getAggregateId() {
		return aggregateId;
	}

	public void setAggregateId(String aggregateId) {
		this.aggregateId = aggregateId;
	}

	public Long getAggregateSequenceNumber() {
		return aggregateSequenceNumber;
	}

	public void setAggregateSequenceNumber(Long aggregateSequenceNumber) {
		this.aggregateSequenceNumber = aggregateSequenceNumber;
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
