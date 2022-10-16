package org.eventrails.server.es.eventstore;

import lombok.*;
import org.eventrails.modeling.gateway.PublishedEvent;
import org.eventrails.modeling.messaging.message.EventMessage;
import org.eventrails.server.config.JsonConverter;

import javax.persistence.*;
import java.time.Instant;

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

	private String eventId;
	private Long aggregateSequenceNumber;
	private String aggregateId;
	@Column(columnDefinition = "JSON")
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
		event.setEventId(getEventId());
		event.setEventName(getEventName());
		return event;
	}


}
