package org.evento.server.es.eventstore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.evento.common.modeling.messaging.dto.PublishedEvent;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.server.config.JsonConverter;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Entity
@Table(name = "es__events")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EventStoreEntry {

	private String context;

	@Id
	private Long eventSequenceNumber;
	private String aggregateId;

	@Convert(converter = JsonConverter.class)
	private EventMessage<?> eventMessage;
	private String eventName;
	private Timestamp createdAt;
	private Timestamp deletedAt;


	public PublishedEvent toPublishedEvent() {
		var event = new PublishedEvent();
		event.setAggregateId(getAggregateId());
		event.setCreatedAt(getCreatedAt());
		event.setEventMessage(getEventMessage());
		event.setEventSequenceNumber(getEventSequenceNumber());
		event.setEventName(getEventName());
		return event;
	}


}
