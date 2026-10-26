package com.evento.server.es.eventstore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.server.config.JsonConverter;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
	// es__events keeps UTC wall time in plain `timestamp` columns (now() at time zone 'utc'): read
	// as LocalDateTime, so neither the JVM's zone nor a daylight-saving change shifts it.
	private LocalDateTime createdAt;
	private LocalDateTime deletedAt;

	/** When the event was stored, {@code null} if unknown. */
	public Instant createdAtInstant() {
		return createdAt == null ? null : createdAt.toInstant(ZoneOffset.UTC);
	}

	/** When the event's aggregate was deleted, {@code null} if it was not. */
	public Instant deletedAtInstant() {
		return deletedAt == null ? null : deletedAt.toInstant(ZoneOffset.UTC);
	}


	public PublishedEvent toPublishedEvent() {
		var event = new PublishedEvent();
		event.setAggregateId(getAggregateId());
		event.setCreatedAt(createdAt == null ? null : createdAtInstant().toEpochMilli());
		event.setEventMessage(getEventMessage());
		event.setEventSequenceNumber(getEventSequenceNumber());
		event.setEventName(getEventName());
		return event;
	}


}
