package org.evento.server.es.eventstore;


import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventStoreRepository extends JpaRepository<EventStoreEntry, Long> {

    @Query(value = "select event_sequence_number, context, aggregate_id, created_at, event_message as event_message, event_name, deleted_at from es__events " +
            "where aggregate_id = ?1 and es__events.event_sequence_number > ?2 and event_sequence_number < ?3 and deleted_at is null order by event_sequence_number",
            nativeQuery = true)
    List<EventStoreEntry> fetchEvents(String context, Long seq, Long seqTo, Pageable pageable);

    @Query(value = "select event_sequence_number, context, aggregate_id, created_at, event_message as event_message, event_name, deleted_at from es__events " +
            "where es__events.event_sequence_number > ?1 and event_sequence_number < ?2 and deleted_at is null order by event_sequence_number",
            nativeQuery = true)
    List<EventStoreEntry> fetchEvents(Long seq, Long seqTo, Pageable pageable);

    @Query(value = "select event_sequence_number, context, aggregate_id, created_at, event_message as event_message, event_name, deleted_at from es__events " +
            "where context = ?1 and es__events.event_sequence_number > ?2 and event_sequence_number < ?3 and event_name in ?4 and deleted_at is null order by event_sequence_number",
            nativeQuery = true)
    List<EventStoreEntry> fetchEvents(String context, Long seq, Long seqTo, List<String> eventNames, Pageable pageable);

    @Query(value = "select event_sequence_number, context, aggregate_id, created_at, event_message as event_message, event_name, deleted_at from es__events " +
            "where es__events.event_sequence_number > ?1 and event_sequence_number < ?2 and event_name in ?3 and deleted_at is null order by event_sequence_number",
            nativeQuery = true)
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
