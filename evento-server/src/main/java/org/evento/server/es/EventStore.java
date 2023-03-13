package org.evento.server.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.common.modeling.state.SerializedAggregateState;
import org.evento.common.serialization.ObjectMapperUtils;
import org.evento.common.utils.Snowflake;
import org.evento.server.es.eventstore.EventStoreEntry;
import org.evento.server.es.eventstore.EventStoreRepository;
import org.evento.server.es.snapshot.Snapshot;
import org.evento.server.es.snapshot.SnapshotRepository;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class EventStore {

	private static final long DELAY = 69;
	private final EventStoreRepository eventStoreRepository;
	private final SnapshotRepository snapshotRepository;
	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper mapper = ObjectMapperUtils.getPayloadObjectMapper();
	private final Snowflake snowflake = new Snowflake();


	public EventStore(EventStoreRepository repository, SnapshotRepository snapshotRepository, JdbcTemplate jdbcTemplate) {
		this.eventStoreRepository = repository;
		this.snapshotRepository = snapshotRepository;
		this.jdbcTemplate = jdbcTemplate;
	}


	public List<EventStoreEntry> fetchAggregateState(String aggregateId) {
		return eventStoreRepository.findAllByAggregateIdOrderByEventSequenceNumberAsc(aggregateId);
	}

	public List<EventStoreEntry> fetchAggregateState(String aggregateId, Long seq) {
		return eventStoreRepository.findAllByAggregateIdAndEventSequenceNumberAfterOrderByEventSequenceNumberAsc(aggregateId, seq);
	}

	public List<EventStoreEntry> fetchEvents(Long seq, int limit) {
		if (seq == null) seq = -1L;
		return eventStoreRepository.findAllByEventSequenceNumberAfterAndEventSequenceNumberBeforeOrderByEventSequenceNumberAsc(seq,
				snowflake.forInstant(Instant.now().minus(DELAY, ChronoUnit.MILLIS)), PageRequest.of(0, limit));
	}

	public List<EventStoreEntry> fetchEvents(Long seq, int limit, List<String> eventNames) {
		if (seq == null) seq = -1L;
		return eventStoreRepository.findAllByEventSequenceNumberAfterAndEventSequenceNumberBeforeAndEventNameInOrderByEventSequenceNumberAsc(
				seq, snowflake.forInstant(Instant.now().minus(DELAY, ChronoUnit.MILLIS)),
				eventNames, PageRequest.of(0, limit));
	}

	public Snapshot fetchSnapshot(String aggregateId) {
		return snapshotRepository.findById(aggregateId).orElse(null);
	}

	public Snapshot saveSnapshot(String aggregateId, Long eventSequenceNumber, SerializedAggregateState<?> aggregateState) {
		var snapshot = new Snapshot();
		snapshot.setAggregateId(aggregateId);
		snapshot.setEventSequenceNumber(eventSequenceNumber);
		snapshot.setAggregateState(aggregateState);
		snapshot.setUpdatedAt(Instant.now());
		return snapshotRepository.save(snapshot);
	}

	public long getLastEventSequenceNumber() {
		var v = eventStoreRepository.getLastEventSequenceNumber();
		return v == null ? 0 : v;
	}

	public void publishEvent(EventMessage<?> eventMessage, String aggregateId) {

		try
		{
			var time = Instant.now().toEpochMilli();
			jdbcTemplate.update(
					"INSERT INTO es__events " +
							"(event_sequence_number," +
							"aggregate_id, created_at, event_message, event_name) " +
							"value  (?, ?, ?, ?, ?)",
					snowflake.nextId(),
					aggregateId,
					time,
					mapper.writeValueAsBytes(eventMessage),
					eventMessage.getEventName()
			);

		} catch (JsonProcessingException e)
		{
			throw new RuntimeException(e);
		}
	}

	public void publishEvent(EventMessage<?> eventMessage) {
		try
		{
			var time = Instant.now().toEpochMilli();
			jdbcTemplate.update(
					"INSERT INTO es__events " +
							"(event_sequence_number, aggregate_id, created_at, event_message, event_name) " +
							"value ( ?, ?, ?,?,?)",
					snowflake.nextId(),
					null,
					time,
					mapper.writeValueAsBytes(eventMessage),
					eventMessage.getEventName()
			);
		} catch (JsonProcessingException e)
		{
			throw new RuntimeException(e);
		}
	}

	public Long getLastAggregateSequenceNumber(String aggregateId) {
		return eventStoreRepository.getLastAggregateSequenceNumber(aggregateId);
	}
}
