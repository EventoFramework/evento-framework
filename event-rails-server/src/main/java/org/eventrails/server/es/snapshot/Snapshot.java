package org.eventrails.server.es.snapshot;

import lombok.Getter;

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
	public String aggregateState;

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

	public String getAggregateState() {
		return aggregateState;
	}

	public void setAggregateState(String aggregateState) {
		this.aggregateState = aggregateState;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
