package com.evento.server.es;

import com.evento.server.es.utils.ExpiringLruCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.EventMessage;
import com.evento.common.modeling.state.SerializedAggregateState;
import com.evento.common.serialization.ObjectMapperUtils;
import com.evento.common.utils.Snowflake;
import com.evento.server.es.eventstore.EventStoreEntry;
import com.evento.server.es.eventstore.EventStoreRepository;
import com.evento.server.es.snapshot.Snapshot;
import com.evento.server.es.snapshot.SnapshotRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class EventStore {

    private static final Logger logger = LogManager.getLogger(EventStore.class);

    private static final String ES_LOCK = "es-lock";
    private final long DELAY;
    private final EventStoreMode MODE;
    private final EventStoreRepository eventStoreRepository;
    private final SnapshotRepository snapshotRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = ObjectMapperUtils.getPayloadObjectMapper();
    private final Snowflake snowflake = new Snowflake();
    private final Connection lockConnection;

    private final ExpiringLruCache<String, Snapshot> snapshotCache;
    private final ExpiringLruCache<String, List<EventStoreEntry>> eventsCache;

    private static final ConcurrentHashMap<String, LockWrapper> locks = new ConcurrentHashMap<>();

    public EventStore(EventStoreRepository repository,
                      SnapshotRepository snapshotRepository,
                      JdbcTemplate jdbcTemplate,
                      DataSource dataSource,
                      @Value("${evento.es.aggregate.state.cache.size:1024}")
                      int aggregateSnapshotCacheSize,
                      @Value("${evento.es.aggregate.events.cache.size:1024}")
                      int aggregateEventsCacheSize,
                      @Value("${evento.es.aggregate.state.cache.expiry:150000}")
                      int aggregateSnapshotCacheExpiry,
                      @Value("${evento.es.aggregate.events.cache.expiry:150000}")
                      int aggregateEventsCacheExpiry,
                      @Value("${evento.es.fetch.delay:69}")
                      int fetchDelay,
                      @Value("${evento.es.mode:APES}")
                      EventStoreMode mode) throws SQLException {
        this.eventStoreRepository = repository;
        this.snapshotRepository = snapshotRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.lockConnection = dataSource.getConnection();
        snapshotCache = new ExpiringLruCache<>(aggregateSnapshotCacheSize, aggregateSnapshotCacheExpiry, TimeUnit.MILLISECONDS);
        eventsCache = new ExpiringLruCache<>(aggregateEventsCacheSize, aggregateEventsCacheExpiry, TimeUnit.MILLISECONDS);
        DELAY = fetchDelay;
        MODE = mode;

        logger.info("Initializing EventStore");
        if (mode == EventStoreMode.CPES) {
            logger.info("Mode: CPES");
        } else {
            logger.info("Mode: APES - Fetch Delay: {}", fetchDelay);
        }
        logger.info("Aggregate Snapshot Cache Size: {} - TTL: {}", aggregateSnapshotCacheSize, aggregateEventsCacheExpiry);
        logger.info("Aggregate Story Cache Size: {} - TTL: {}", aggregateEventsCacheSize, aggregateEventsCacheExpiry);

    }

    public Page<EventStoreEntry> searchEvents(String aggregateIdentifier,
                                              String eventName, String context,
                                              Integer eventSequenceNumber,
                                              Timestamp createdAtFrom,
                                              Timestamp createdAtTo,
                                              String contentQuery,
                                              int page,
                                              int size,
                                              Sort.Direction sort,
                                              String sortBy) {

        var predicates = new ArrayList<Specification<EventStoreEntry>>();

        if(aggregateIdentifier != null && !aggregateIdentifier.isBlank()){
            predicates.add(
                    (r,o,cb) -> cb.equal(r.get("aggregateId"),aggregateIdentifier)
            );
        }

        if(eventName != null && !eventName.isBlank()){
            predicates.add(
                    (r,o,cb) -> cb.equal(r.get("eventName"),eventName)
            );
        }

        if(context != null && !context.isBlank()){
            predicates.add(
                    (r,o,cb) -> cb.equal(r.get("context"),context)
            );
        }

        if(eventSequenceNumber != null ){
            predicates.add(
                    (r,o,cb) -> cb.equal(r.get("eventSequenceNumber"),eventSequenceNumber)
            );
        }

        if(createdAtFrom != null ){
            predicates.add(
                    (r,o,cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"),createdAtTo)
            );
        }

        if(createdAtTo != null ){
            predicates.add(
                    (r,o,cb) -> cb.lessThanOrEqualTo(r.get("createdAt"),createdAtFrom)
            );
        }

        if(contentQuery != null  && !contentQuery.isBlank() ){
            predicates.add(
                    (r,o,cb) -> cb.like(r.get("eventMessage"),contentQuery)
            );
        }

        return eventStoreRepository.findAll(
                Specification.allOf(predicates),
                PageRequest.of(page, size, Sort.by(sort, sortBy))
        );




    }

    public Page<Snapshot> searchSnapshots(String aggregateId,
                                     int page,
                                     int size,
                                     Sort.Direction sort,
                                     String sortBy){
        var s = new Snapshot();
        if(aggregateId != null && !aggregateId.isBlank())
            s.setAggregateId(aggregateId);

        return snapshotRepository.findAll(Example.of(s), PageRequest.of(
                page, size, Sort.by(sort, sortBy)
        ));
    }

    private static class LockWrapper {
        private final Semaphore lock = new Semaphore(1);
        private final AtomicInteger numberOfThreadsInQueue = new AtomicInteger(1);

        private LockWrapper addThreadInQueue() {
            numberOfThreadsInQueue.incrementAndGet();
            return this;
        }

        private int removeThreadFromQueue() {
            return numberOfThreadsInQueue.decrementAndGet();
        }

    }

    public void acquire(String key) {
        if (key == null) return;
        LockWrapper lockWrapper = locks.compute(key, (k, v) -> v == null ? new LockWrapper() : v.addThreadInQueue());
        lockWrapper.lock.acquireUninterruptibly();
        try (var stmt = this.lockConnection.prepareStatement("SELECT pg_advisory_lock(?)")) {
            stmt.setInt(1, key.hashCode());
            var resultSet = stmt.executeQuery();
            resultSet.next();
            if (resultSet.wasNull()) throw new IllegalMonitorStateException();
            var status = resultSet.getString(1);
            if (!"".equals(status)) throw new IllegalMonitorStateException();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    public void release(String key) {
        if (key == null) return;
        LockWrapper lockWrapper = locks.get(key);
        lockWrapper.lock.release();
        if (lockWrapper.removeThreadFromQueue() == 0) {
            // NB : We pass in the specific value to remove to handle the case where another thread would queue right before the removal
            locks.remove(key, lockWrapper);
        }
        try (var stmt = lockConnection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            stmt.setInt(1, key.hashCode());
            var resultSet = stmt.executeQuery();
            resultSet.next();
            if (resultSet.wasNull()) throw new IllegalMonitorStateException();
            var status = resultSet.getBoolean(1);
            if (!status) throw new IllegalMonitorStateException("Lock key: " + key);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<EventStoreEntry> fetchEvents(String context, Long seq, int limit) {
        if (seq == null) seq = -1L;
        if (MODE == EventStoreMode.CPES) {
            return eventStoreRepository.fetchEvents(
                    context.replace("*", "%"),
                    seq, PageRequest.of(0, limit));
        }
        return eventStoreRepository.fetchEvents(
                context.replace("*", "%"),
                seq,
                snowflake.forInstant(Instant.now().minus(DELAY, ChronoUnit.MILLIS)), PageRequest.of(0, limit));
    }

    public List<EventStoreEntry> fetchEvents(String context, Long seq, int limit, List<String> eventNames) {
        if (seq == null) seq = -1L;
        if (MODE == EventStoreMode.CPES) {
            return eventStoreRepository.fetchEvents(
                    context.replace("*", "%"),
                    seq,
                    eventNames, PageRequest.of(0, limit));
        }
        return eventStoreRepository.fetchEvents(
                context.replace("*", "%"),
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

    public long publishEvent(EventMessage<?> eventMessage, String aggregateId) {

        var id = snowflake.nextId();
        try {
            if (MODE == EventStoreMode.APES) {
                jdbcTemplate.update(
                        "INSERT INTO es__events " +
                                "(event_sequence_number," +
                                "aggregate_id, event_message, event_name, context) " +
                                "values  (?, ?, ?, ?, ?)",
                        id,
                        aggregateId,
                        mapper.writeValueAsString(eventMessage),
                        eventMessage.getEventName(),
                        eventMessage.getContext()
                );
            } else {
                acquire(ES_LOCK);
                id = jdbcTemplate.queryForRowSet("select nextval('event_sequence_number_serial') as event_sequence_number")
                        .getLong("event_sequence_number");
                jdbcTemplate.update(
                        "INSERT INTO es__events " +
                                "(event_sequence_number, aggregate_id, event_message, event_name, context) " +
                                "values  (? ,?, ?, ?, ?)",
                        id,
                        aggregateId,
                        mapper.writeValueAsString(eventMessage),
                        eventMessage.getEventName(),
                        eventMessage.getContext()
                );
                release(ES_LOCK);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return id;
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
        } catch (Exception e) {
            return 0.0;
        }
    }

    public Long getAggregateCount() {
        return eventStoreRepository.getAggregateCount();
    }

    public void deleteAggregate(String aggregateIdentifier) {
        jdbcTemplate.update(
                "UPDATE es__events " +
                        "set deleted_at = current_timestamp where aggregate_id = ?",
                aggregateIdentifier
        );
        jdbcTemplate.update(
                "UPDATE es__snapshot " +
                        "set deleted_at = current_timestamp where aggregate_id = ?",
                aggregateIdentifier
        );
        snapshotCache.remove(aggregateIdentifier);
        eventsCache.remove(aggregateIdentifier);
    }

    /**
     * Fetches the aggregate story for a given aggregate ID.
     *
     * @param aggregateId The ID of the aggregate.
     * @param invalidateAggregateCaches A flag indicating whether to invalidate the aggregate caches.
     * @param invalidateAggregateSnapshot A flag indicating whether to invalidate the aggregate snapshot.
     * @return The aggregate story, consisting of the serialized aggregate state and a list of domain events.
     */
    public AggregateStory fetchAggregateStory(String aggregateId,
                                              boolean invalidateAggregateCaches,
                                              boolean invalidateAggregateSnapshot) {
        Assert.isTrue(aggregateId != null, "Fetching aggregate state without an aggregate Id");
        if(invalidateAggregateCaches){
            snapshotCache.remove(aggregateId);
            eventsCache.remove(aggregateId);
        }
        if (!invalidateAggregateSnapshot && !snapshotCache.containsKey(aggregateId)) {
            snapshotCache.put(aggregateId, snapshotRepository.findById(aggregateId).orElse(null));
        }
        var snapshot = invalidateAggregateSnapshot ? null :  snapshotCache.get(aggregateId);
        var events = eventsCache.getOrDefault(aggregateId, new ArrayList<>());
        SqlRowSet rs;
        var max = 0L;
        var min = events.isEmpty() ? 0L : events.getFirst().getEventSequenceNumber();
        if(snapshot == null){
            max = events.isEmpty() ? 0L : events.getLast().getEventSequenceNumber();
            rs = jdbcTemplate.queryForRowSet(
                    "select event_sequence_number, event_message " +
                            "from es__events " +
                            "where aggregate_id = ? " +
                            "and (es__events.event_sequence_number < ? or es__events.event_sequence_number > ?) " +
                            "order by event_sequence_number",
                    aggregateId,
                    min,
                    max
            );

        }else{
            max = events.isEmpty() ? snapshot.getEventSequenceNumber() : events.getLast().getEventSequenceNumber();
            if(snapshot.getEventSequenceNumber() < min){
                rs = jdbcTemplate.queryForRowSet(
                        "select event_sequence_number, event_message " +
                                "from es__events " +
                                "where aggregate_id = ? " +
                                "and ((es__events.event_sequence_number > ? and es__events.event_sequence_number < ?) or (es__events.event_sequence_number > ?)) " +
                                "order by event_sequence_number",
                        aggregateId,
                        snapshot.getEventSequenceNumber(),
                        events.getFirst().getEventSequenceNumber(),
                        max
                );

            }else{
                rs = jdbcTemplate.queryForRowSet(
                        "select event_sequence_number, event_message " +
                                "from es__events " +
                                "where aggregate_id = ? " +
                                "and es__events.event_sequence_number > ? " +
                                "order by event_sequence_number",
                        aggregateId,
                        max
                );
            }
        }

        var i = 0;
        while (true) {
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
