package org.eventrails.server.es.eventstore;


import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventStoreRepository extends JpaRepository<EventStoreEntry, String> {

	List<EventStoreEntry> findAllByAggregateIdOrderByAggregateSequenceNumberAsc(String aggregateId);

	List<EventStoreEntry> findAllByAggregateIdAndAggregateSequenceNumberAfterOrderByAggregateSequenceNumberAsc(String aggregateId, Long seq);

	List<EventStoreEntry> findAllByEventSequenceNumberAfterOrderByEventSequenceNumberAsc(Long seq);
	List<EventStoreEntry> findAllByEventSequenceNumberAfterOrderByEventSequenceNumberAsc(Long seq, Pageable pageable);

	@Query("select max(e.eventSequenceNumber) from EventStoreEntry e")
	Long getLastEventSequenceNumber();

	@Query("select max(e.aggregateSequenceNumber) from EventStoreEntry e where e.aggregateId = :aggregateId")
	Long getLastAggregateSequenceNumber(String aggregateId);


}
