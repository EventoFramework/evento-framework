package org.evento.server.es.eventstore;


import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventStoreRepository extends JpaRepository<EventStoreEntry, Long> {

    @Query("select e from EventStoreEntry e where e.aggregateId = ?1 and e.deletedAt is null order by e.eventSequenceNumber asc ")
    List<EventStoreEntry> fetchAggregateStory(String aggregateId);

    @Query("select e from EventStoreEntry e where e.aggregateId = ?1 and e.eventSequenceNumber > ?2 and e.deletedAt is null order by e.eventSequenceNumber asc ")
    List<EventStoreEntry> fetchAggregateStory(String aggregateId, Long seq);

    @Query("select e from EventStoreEntry e where e.context = ?1 and  e.eventSequenceNumber > ?2 and  e.eventSequenceNumber < ?3 and e.deletedAt is null order by e.eventSequenceNumber asc ")
    List<EventStoreEntry> fetchEvents(String context, Long seq, Long seqTo, Pageable pageable);


    @Query("select e from EventStoreEntry e where e.eventSequenceNumber > ?1 and  e.eventSequenceNumber < ?2 and e.deletedAt is null order by e.eventSequenceNumber asc ")
    List<EventStoreEntry> fetchEvents(Long seq, Long seqTo, Pageable pageable);

    @Query("select e from EventStoreEntry e where e.context = ?1 and  e.eventSequenceNumber > ?2 and  e.eventSequenceNumber < ?3 and e.eventName in ?4 and e.deletedAt is null order by e.eventSequenceNumber asc ")
    List<EventStoreEntry> fetchEvents(String context, Long seq, Long seqTo, List<String> eventNames, Pageable pageable);

    @Query("select e from EventStoreEntry e where  e.eventSequenceNumber > ?1 and  e.eventSequenceNumber < ?2 and e.eventName in ?3 and e.deletedAt is null order by e.eventSequenceNumber asc ")
    List<EventStoreEntry> fetchEvents(Long seq, Long seqTo, List<String> eventNames, Pageable pageable);

    @Query("select max(e.eventSequenceNumber) from EventStoreEntry e")
    Long getLastEventSequenceNumber();

    @Query("select max(e.eventSequenceNumber) from EventStoreEntry e where e.aggregateId = :aggregateId")
    Long getLastAggregateSequenceNumber(String aggregateId);


    @Query(value = "select count(*) / ((max(created_at) - min(created_at))/1000) " +
            "from (select created_at from es__events order by event_sequence_number desc limit 100) r", nativeQuery = true)
    Double getPublicationRatio();

    @Query("select count(distinct e.aggregateId) from EventStoreEntry e")
    Long getAggregateCount();
}
