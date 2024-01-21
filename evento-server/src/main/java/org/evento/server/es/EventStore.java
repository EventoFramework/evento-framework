package org.evento.server.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evento.common.modeling.messaging.message.application.DomainEventMessage;
import org.evento.common.modeling.messaging.message.application.EventMessage;
import org.evento.common.modeling.state.SerializedAggregateState;
import org.evento.common.serialization.ObjectMapperUtils;
import org.evento.common.utils.Context;
import org.evento.common.utils.Snowflake;
import org.evento.server.es.eventstore.EventStoreEntry;
import org.evento.server.es.eventstore.EventStoreRepository;
import org.evento.server.es.snapshot.Snapshot;
import org.evento.server.es.snapshot.SnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class EventStore {

    private static final long DELAY = 69;
    private final EventStoreRepository eventStoreRepository;
    private final SnapshotRepository snapshotRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = ObjectMapperUtils.getPayloadObjectMapper();
    private final Snowflake snowflake = new Snowflake();
    private final Connection lockConnection;

    private final LruCache<String, Snapshot> snapshotCache;
    private final LruCache<String, List<EventStoreEntry>> eventsCache;

    public EventStore(EventStoreRepository repository,
                      SnapshotRepository snapshotRepository,
                      JdbcTemplate jdbcTemplate,
                      DataSource dataSource,
                      @Value("${evento.es.aggregate.state.cache.size:1024}")
                      int aggregateSnapshotCacheSize,
                      @Value("${evento.es.aggregate.events.cache.size:1024}")
                      int aggregateEventsCacheSize) throws SQLException {
        this.eventStoreRepository = repository;
        this.snapshotRepository = snapshotRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.lockConnection = dataSource.getConnection();
        snapshotCache = new LruCache<>(aggregateSnapshotCacheSize);
        eventsCache = new LruCache<>(aggregateEventsCacheSize);
    }

    public void acquire(String string) {
        if (string == null) return;
        try (var stmt = this.lockConnection.prepareStatement("SELECT pg_advisory_lock(?)")) {
            stmt.setInt(1, string.hashCode());
            var resultSet = stmt.executeQuery();
            resultSet.next();
            if (resultSet.wasNull()) throw new IllegalMonitorStateException();
            var status = resultSet.getString(1);
            if (!"".equals(status)) throw new IllegalMonitorStateException();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    public void release(String string) {
        if (string == null) return;
        try (var stmt = lockConnection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            stmt.setInt(1, string.hashCode());
            var resultSet = stmt.executeQuery();
            resultSet.next();
            if (resultSet.wasNull()) throw new IllegalMonitorStateException();
            var status = resultSet.getBoolean(1);
            if (!status) throw new IllegalMonitorStateException();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<EventStoreEntry> fetchEvents(String context, Long seq, int limit) {
        if (seq == null) seq = -1L;
        return Context.ALL.equals(context) ?
                eventStoreRepository.fetchEvents(
                        seq,
                        snowflake.forInstant(Instant.now().minus(DELAY, ChronoUnit.MILLIS)), PageRequest.of(0, limit)) :
                eventStoreRepository.fetchEvents(
                        context,
                        seq,
                        snowflake.forInstant(Instant.now().minus(DELAY, ChronoUnit.MILLIS)), PageRequest.of(0, limit));
    }

    public List<EventStoreEntry> fetchEvents(String context, Long seq, int limit, List<String> eventNames) {
        if (seq == null) seq = -1L;
        return Context.ALL.equals(context) ? eventStoreRepository.fetchEvents(
                seq, snowflake.forInstant(Instant.now().minus(DELAY, ChronoUnit.MILLIS)),
                eventNames, PageRequest.of(0, limit)) :
                eventStoreRepository.fetchEvents(
                        context,
                        seq, snowflake.forInstant(Instant.now().minus(DELAY, ChronoUnit.MILLIS)),
                        eventNames, PageRequest.of(0, limit));
    }


    public void saveSnapshot(String aggregateId, Long eventSequenceNumber, SerializedAggregateState<?> aggregateState) {
        var snapshot = new Snapshot();
        snapshot.setAggregateId(aggregateId);
        snapshot.setEventSequenceNumber(eventSequenceNumber);
        snapshot.setAggregateState(aggregateState);
        snapshot.setUpdatedAt(Instant.now());
        snapshotRepository.save(snapshot);
        snapshotCache.put(aggregateId, snapshot);
    }

    public long getLastEventSequenceNumber() {
        var v = eventStoreRepository.getLastEventSequenceNumber();
        return v == null ? 0 : v;
    }

    public void publishEvent(EventMessage<?> eventMessage, String aggregateId) {

        try {
            jdbcTemplate.update(
                    "INSERT INTO es__events " +
                            "(event_sequence_number," +
                            "aggregate_id, event_message, event_name, context) " +
                            "values  (?, ?, ?, ?, ?)",
                    snowflake.nextId(),
                    aggregateId,
                    mapper.writeValueAsString(eventMessage),
                    eventMessage.getEventName(),
                    eventMessage.getContext()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void publishEvent(EventMessage<?> eventMessage) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO es__events " +
                            "(event_sequence_number, aggregate_id, event_message, event_name, context) values " +
                            "( ?, ?,?,?,?)",
                    snowflake.nextId(),
                    null,
                    mapper.writeValueAsString(eventMessage),
                    eventMessage.getEventName(),
                    eventMessage.getContext()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Long getLastAggregateSequenceNumber(String aggregateId) {
        return eventStoreRepository.getLastAggregateSequenceNumber(aggregateId);
    }

    public Long getSize() {
        return eventStoreRepository.count();
    }

    public Double getRecentPublicationRation() {
        try {
            return eventStoreRepository.getPublicationRatio();
        }catch (Exception e){
            return 0.0;
        }
    }

    public Long getAggregateCount() {
        return eventStoreRepository.getAggregateCount();
    }

    public void deleteAggregate(String aggregateIdentifier) {
        jdbcTemplate.update(
                "UPDATE es__events " +
                        "set deleted_at = ? where aggregate_id = ?",
                Instant.now(),
                aggregateIdentifier
        );
        snapshotRepository.deleteAggregate(aggregateIdentifier);
        snapshotCache.remove(aggregateIdentifier);
        eventsCache.remove(aggregateIdentifier);
    }

    private static class LruCache<A, B> extends LinkedHashMap<A, B> {
        private final int maxEntries;
        public LruCache(final int maxEntries) {
            super(maxEntries + 1, 1.0f, true);
            this.maxEntries = maxEntries;
        }
        @Override
        protected boolean removeEldestEntry(final Map.Entry<A, B> eldest) {
            return super.size() > maxEntries;
        }
    }

    public AggregateStory fetchAggregateStory(String aggregateId) {
        Assert.isTrue(aggregateId!=null, "getAggregateId() return null!");
        if (!snapshotCache.containsKey(aggregateId)) {
            snapshotCache.put(aggregateId, snapshotRepository.findById(aggregateId).orElse(null));
        }
        var snapshot = snapshotCache.get(aggregateId);
        var events = eventsCache.getOrDefault(aggregateId, new ArrayList<>());
        var min = events.isEmpty() ? 0L : events.get(0).getEventSequenceNumber();
        var max = events.isEmpty() ? 0L : events.get(events.size() - 1).getEventSequenceNumber();
        var i = 0;
        var rs = jdbcTemplate.queryForRowSet(
                "select event_sequence_number, event_message " +
                        "from es__events " +
                        "where aggregate_id = ? " +
                        "and (es__events.event_sequence_number < ? or es__events.event_sequence_number > ?) " +
                        "and deleted_at is null " +
                        "order by event_sequence_number",
                aggregateId,
                min,
                max
        );
        while (true){
            try {
                if (!rs.next()) break;
                var entry = new EventStoreEntry();
                entry.setEventSequenceNumber(rs.getLong(1));
                entry.setEventMessage(mapper.readValue(rs.getString(2), DomainEventMessage.class));
                if (entry.getEventSequenceNumber() > max) {
                    events.add(entry);
                } else {
                    events.add(i++, entry);
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

        }
        eventsCache.put(aggregateId, events);
        return new AggregateStory(
                snapshot == null ? new SerializedAggregateState<>(null) : snapshot.getAggregateState(),
                events.stream()
                        .filter(e -> snapshot == null || e.getEventSequenceNumber() > snapshot.getEventSequenceNumber())
                        .map(e -> (DomainEventMessage) e.getEventMessage())
                        .toList()
        );
    }
}
