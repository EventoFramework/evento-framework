package org.evento.server.es.eventstore;


import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventStoreRepository extends JpaRepository<EventStoreEntry, Long> {

	List<EventStoreEntry> findAllByAggregateIdOrderByEventSequenceNumberAsc(String aggregateId);

	List<EventStoreEntry> findAllByAggregateIdAndEventSequenceNumberAfterOrderByEventSequenceNumberAsc(String aggregateId, Long seq);


	List<EventStoreEntry> findAllByEventSequenceNumberAfterAndCreatedAtBeforeOrderByEventSequenceNumberAsc(Long seq, Long timestamp, Pageable pageable);
	List<EventStoreEntry> findAllByEventSequenceNumberAfterAndCreatedAtBeforeAndEventNameInOrderByEventSequenceNumberAsc(Long seq, Long timestamp, List<String> eventNames,Pageable pageable);

	List<EventStoreEntry> findAllByEventSequenceNumberAfterAndEventSequenceNumberBeforeOrderByEventSequenceNumberAsc(Long seq, Long seqTo, Pageable pageable);
	List<EventStoreEntry> findAllByEventSequenceNumberAfterAndEventSequenceNumberBeforeAndEventNameInOrderByEventSequenceNumberAsc(Long seq, Long seqTo, List<String> eventNames,Pageable pageable);

	@Query("select max(e.eventSequenceNumber) from EventStoreEntry e")
	Long getLastEventSequenceNumber();

	@Query("select max(e.eventSequenceNumber) from EventStoreEntry e where e.aggregateId = :aggregateId")
	Long getLastAggregateSequenceNumber(String aggregateId);


	@Query(value = "select count(*) / ((max(created_at) - min(created_at))/1000) " +
			"from (select created_at from es__events order by event_sequence_number desc limit 100) r", nativeQuery = true)
    Double getPublicationRatio();
}
