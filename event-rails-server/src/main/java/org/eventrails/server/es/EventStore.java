package org.eventrails.server.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eventrails.modeling.messaging.message.EventMessage;
import org.eventrails.modeling.state.SerializedAggregateState;
import org.eventrails.modeling.utils.ObjectMapperUtils;
import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.server.es.eventstore.EventStoreRepository;
import org.eventrails.server.es.snapshot.Snapshot;
import org.eventrails.server.es.snapshot.SnapshotRepository;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EventStore {

	private static final String ES_LOCK = "EX_LOCK";
	private final EventStoreRepository eventStoreRepository;
	private final SnapshotRepository snapshotRepository;
	private final LockRegistry lockRegistry;

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper mapper = ObjectMapperUtils.getPayloadObjectMapper();


	public EventStore(EventStoreRepository repository, SnapshotRepository snapshotRepository, LockRegistry lockRegistry, JdbcTemplate jdbcTemplate) {
		this.eventStoreRepository = repository;
		this.snapshotRepository = snapshotRepository;
		this.lockRegistry = lockRegistry;
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<EventStoreEntry> fetchAggregateStory(String aggregateId) {
		return eventStoreRepository.findAllByAggregateIdOrderByAggregateSequenceNumberAsc(aggregateId);
	}

	public List<EventStoreEntry> fetchAggregateStory(String aggregateId, Long seq) {
		return eventStoreRepository.findAllByAggregateIdAndAggregateSequenceNumberAfterOrderByAggregateSequenceNumberAsc(aggregateId, seq);
	}

	public List<EventStoreEntry> fetchEvents(Long seq) {
		if (seq == null) seq = -1L;
		return eventStoreRepository.findAllByEventSequenceNumberAfterOrderByEventSequenceNumberAsc(seq);
	}

	public Snapshot fetchSnapshot(String aggregateId) {
		return snapshotRepository.findById(aggregateId).orElse(null);
	}

	public Snapshot saveSnapshot(String aggregateId, Long aggregateSequenceNumber, SerializedAggregateState<?> aggregateState) {
		var snapshot = new Snapshot();
		snapshot.setAggregateId(aggregateId);
		snapshot.setAggregateSequenceNumber(aggregateSequenceNumber);
		snapshot.setAggregateState(aggregateState);
		snapshot.setUpdatedAt(Instant.now());
		return snapshotRepository.save(snapshot);
	}

	public Long getLastEventSequenceNumber() {
		return eventStoreRepository.getLastEventSequenceNumber();
	}

	public Long publishEvent(EventMessage<?> eventMessage, String aggregateId) {

		try
		{
			Long aggregateSequenceNumber = (Long) jdbcTemplate.queryForMap("select ifnull(max(aggregate_sequence_number) + 1,1) as a from es__events where aggregate_id = ?", aggregateId)
						.get("a");
			jdbcTemplate.update(
					"INSERT INTO es__events " +
							"(event_id, aggregate_id, aggregate_sequence_number, created_at, event_message, event_name) " +
							"select  ?, ?, ?, " +
							"ROUND(UNIX_TIMESTAMP(CURTIME(4)) * 1000),?,?",
					UUID.randomUUID().toString(),
					aggregateId,
					aggregateSequenceNumber,
					mapper.writeValueAsString(eventMessage),
					eventMessage.getEventName()
					);
			return aggregateSequenceNumber;
		} catch (JsonProcessingException e)
		{
			throw new RuntimeException(e);
		}
	}

	public void publishEvent(EventMessage<?> eventMessage) {
		try
		{
			jdbcTemplate.update(
					"INSERT INTO es__events " +
							"(event_id, aggregate_id, aggregate_sequence_number, created_at, event_message, event_name) " +
							"select  ?, ?, ?, " +
							"ROUND(UNIX_TIMESTAMP(CURTIME(4)) * 1000),?,?",
					UUID.randomUUID().toString(),
					null,
					null,
					mapper.writeValueAsString(eventMessage),
					eventMessage.getEventName()
			);
		} catch (JsonProcessingException e)
		{
			throw new RuntimeException(e);
		}
	}
}
