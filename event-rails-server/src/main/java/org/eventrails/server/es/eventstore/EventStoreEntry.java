package org.eventrails.server.es.eventstore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.eventrails.modeling.gateway.PublishedEvent;

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
	private String eventId;

	private Long eventSequenceNumber;

	private Long aggregateSequenceNumber;
	private String aggregateId;
	@Column(columnDefinition = "JSON")
	private String eventMessage;
	private String eventName;
	private Instant createdAt;



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
