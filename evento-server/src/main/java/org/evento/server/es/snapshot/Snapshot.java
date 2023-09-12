package org.evento.server.es.snapshot;

import org.evento.common.modeling.state.SerializedAggregateState;
import org.evento.server.config.JsonConverter;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "es__snapshot")
public class Snapshot {
	@Id
	private String snapshotId;
	private String context;
	private String aggregateId;
	private Long eventSequenceNumber;
	@Column(columnDefinition = "BLOB")
	@Convert(converter = JsonConverter.class)
	private SerializedAggregateState<?> aggregateState;

	private Instant updatedAt;

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

	public String getSnapshotId() {
		return snapshotId;
	}

	public void setSnapshotId(String snapshotId) {
		this.snapshotId = snapshotId;
	}

	public String getContext() {
		return context;
	}

	public void setContext(String context) {
		this.context = context;
	}
}
