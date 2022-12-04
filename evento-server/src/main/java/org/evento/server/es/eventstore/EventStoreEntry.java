package org.evento.server.es.eventstore;

import lombok.*;
import org.evento.common.modeling.messaging.dto.PublishedEvent;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.server.config.JsonConverter;

import javax.persistence.*;

@Entity
@Table(name = "es__events", indexes = {
		@Index(name="aggregate_index", columnList = "aggregateId"),
		@Index(name="event_sequence_index", columnList = "eventSequenceNumber"),
		@Index(name="aggregate_sequence_index", columnList = "aggregateSequenceNumber")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EventStoreEntry {


	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long eventSequenceNumber;
	private Long aggregateSequenceNumber;
	private String aggregateId;

	@Lob
	@Column(columnDefinition = "BLOB")
	@Convert( converter = JsonConverter.class)
	private EventMessage<?> eventMessage;
	private String eventName;
	private Long createdAt;



	public PublishedEvent toPublishedEvent(){
		var event = new PublishedEvent();
		event.setAggregateId(getAggregateId());
		event.setAggregateSequenceNumber(getAggregateSequenceNumber());
		event.setCreatedAt(getCreatedAt());
		event.setEventMessage(getEventMessage());
		event.setEventSequenceNumber(getEventSequenceNumber());
		event.setEventName(getEventName());
		return event;
	}


}
