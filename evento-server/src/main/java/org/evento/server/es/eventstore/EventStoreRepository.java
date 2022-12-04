package org.evento.server.es.eventstore;


import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventStoreRepository extends JpaRepository<EventStoreEntry, Long> {

	List<EventStoreEntry> findAllByAggregateIdOrderByEventSequenceNumberAsc(String aggregateId);

	List<EventStoreEntry> findAllByAggregateIdAndEventSequenceNumberAfterOrderByEventSequenceNumberAsc(String aggregateId, Long seq);


	List<EventStoreEntry> findAllByEventSequenceNumberAfterAndCreatedAtBeforeOrderByEventSequenceNumberAsc(Long seq, Long timestamp, Pageable pageable);

	@Query("select max(e.eventSequenceNumber) from EventStoreEntry e")
	Long getLastEventSequenceNumber();

	@Query("select max(e.eventSequenceNumber) from EventStoreEntry e where e.aggregateId = :aggregateId")
	Long getLastAggregateSequenceNumber(String aggregateId);


}
