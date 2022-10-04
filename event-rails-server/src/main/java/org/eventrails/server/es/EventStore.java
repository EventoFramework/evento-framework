package org.eventrails.server.es;

import org.eventrails.server.es.eventstore.EventStoreEntry;
import org.eventrails.server.es.eventstore.EventStoreRepository;
import org.eventrails.server.es.snapshot.Snapshot;
import org.eventrails.server.es.snapshot.SnapshotRepository;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EventStore {

	private static final String ES_LOCK = "EX_LOCK";
	private final EventStoreRepository eventStoreRepository;
	private final SnapshotRepository snapshotRepository;
	private final LockRegistry lockRegistry;


	public EventStore(EventStoreRepository repository, SnapshotRepository snapshotRepository, LockRegistry lockRegistry) {
		this.eventStoreRepository = repository;
		this.snapshotRepository = snapshotRepository;
		this.lockRegistry = lockRegistry;
	}

	public List<EventStoreEntry> fetchAggregateStory(String aggregateId){
		return eventStoreRepository.findAllByAggregateIdOrderByAggregateSequenceNumberAsc(aggregateId);
	}

	public List<EventStoreEntry> fetchAggregateStory(String aggregateId, Long seq){
		return eventStoreRepository.findAllByAggregateIdAndAggregateSequenceNumberAfterOrderByAggregateSequenceNumberAsc(aggregateId, seq);
	}

	public List<EventStoreEntry> fetchEvents(Long seq){
		if(seq == null) seq = -1L;
		return eventStoreRepository.findAllByEventSequenceNumberAfterOrderByEventSequenceNumberAsc(seq);
	}

	public Snapshot fetchSnapshot(String aggregateId){
		return snapshotRepository.findById(aggregateId).orElse(null);
	}

	public Snapshot saveSnapshot(String aggregateId, Long aggregateSequenceNumber, String aggregateState){
		var snapshot = new Snapshot();
		snapshot.setAggregateId(aggregateId);
		snapshot.setAggregateSequenceNumber(aggregateSequenceNumber);
		snapshot.setAggregateState(aggregateState);
		snapshot.setUpdatedAt(Instant.now());
		return snapshotRepository.save(snapshot);
	}

	public EventStoreEntry publishEvent(String eventMessage, String aggregateId){
		var lock = lockRegistry.obtain(ES_LOCK);
		try{
			lock.lock();
			var entry = new EventStoreEntry();

			entry.setEventId(UUID.randomUUID().toString());

			if(aggregateId != null){
				var aggregateSequenceNumber = eventStoreRepository.getLastAggregateSequenceNumber(aggregateId);
				entry.setAggregateSequenceNumber(aggregateSequenceNumber != null ? aggregateSequenceNumber + 1 : 1);
			}

			var eventSequenceNumber = eventStoreRepository.getLastEventSequenceNumber();
			entry.setEventSequenceNumber(eventSequenceNumber != null ? eventSequenceNumber + 1 : 1);

			entry.setEventMessage(eventMessage);

			entry.setAggregateId(aggregateId);

			entry.setCreatedAt(Instant.now());

			return eventStoreRepository.save(entry);
		}finally
		{
			lock.unlock();
		}
	}

	public EventStoreEntry publishEvent(String eventMessage){
		return publishEvent(eventMessage, null);
	}
}
