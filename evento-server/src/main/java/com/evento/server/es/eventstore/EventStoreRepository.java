package com.evento.server.es.eventstore;


import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * The {@code fetchEvents} queries take a LIKE pattern built by
 * {@code EventStore.contextLikePattern}: {@code !} is the escape character, so literal
 * {@code _}/{@code %} in a context name never act as wildcards. A backslash escape would be
 * read differently depending on {@code standard_conforming_strings}; {@code !} is not.
 */
public interface EventStoreRepository extends JpaRepository<EventStoreEntry, Long>, JpaSpecificationExecutor<EventStoreEntry> {

    @Query(value = "select event_sequence_number, context, aggregate_id, created_at, event_message as event_message, event_name, deleted_at from es__events " +
            "where context like ?1 escape '!' and es__events.event_sequence_number > ?2 and event_sequence_number < ?3 order by event_sequence_number",
            nativeQuery = true)
    List<EventStoreEntry> fetchEvents(String context, Long seq, Long seqTo, Pageable pageable);

    @Query(value = "select event_sequence_number, context, aggregate_id, created_at, event_message as event_message, event_name, deleted_at from es__events " +
            "where context like ?1 escape '!' and es__events.event_sequence_number > ?2 order by event_sequence_number",
            nativeQuery = true)
    List<EventStoreEntry> fetchEvents(String context, Long seq, Pageable pageable);

    @Query(value = "select event_sequence_number, context, aggregate_id, created_at, event_message as event_message, event_name, deleted_at from es__events " +
            "where context like ?1 escape '!' and es__events.event_sequence_number > ?2 and event_sequence_number < ?3 and event_name in ?4 order by event_sequence_number",
            nativeQuery = true)
    List<EventStoreEntry> fetchEvents(String context, Long seq, Long seqTo, List<String> eventNames, Pageable pageable);

    @Query(value = "select event_sequence_number, context, aggregate_id, created_at, event_message as event_message, event_name, deleted_at from es__events " +
            "where context like ?1 escape '!' and es__events.event_sequence_number > ?2 and event_name in ?3 order by event_sequence_number",
            nativeQuery = true)
    List<EventStoreEntry> fetchEvents(String context, Long seq, List<String> eventNames, Pageable pageable);

    @Query("select max(e.eventSequenceNumber) from EventStoreEntry e")
    Long getLastEventSequenceNumber();

    @Query("select max(e.eventSequenceNumber) from EventStoreEntry e where e.aggregateId = :aggregateId")
    Long getLastAggregateSequenceNumber(String aggregateId);


    @Query(value = "select count(*) / greatest(extract('epoch' from coalesce(max(created_at) - min(created_at), interval '1 second')), 1) " +
            "from (select created_at from es__events order by event_sequence_number desc limit 10000) r", nativeQuery = true)
    Double getPublicationRatio();

    @Query("select count(distinct e.aggregateId) from EventStoreEntry e")
    Long getAggregateCount();

}
