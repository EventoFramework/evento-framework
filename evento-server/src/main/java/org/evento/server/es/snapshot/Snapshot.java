package org.evento.server.es.snapshot;

import lombok.Getter;
import lombok.Setter;
import org.evento.common.modeling.state.SerializedAggregateState;
import org.evento.server.config.JsonConverter;

import javax.persistence.*;
import java.time.Instant;

@Setter
@Getter
@Entity
@Table(name = "es__snapshot")
public class Snapshot {
	@Id
	private String aggregateId;
	private Long eventSequenceNumber;
	@Column(columnDefinition = "TEXT")
	@Convert(converter = JsonConverter.class)
	private SerializedAggregateState<?> aggregateState;

	private Instant updatedAt;
	private Instant deletedAt;

}
