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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class EventStore implements BrokerEventStore {

    private static final Logger logger = LogManager.getLogger(EventStore.class);

    /**
     * CPES append: the global append lock, the sequence number and the row in ONE statement,
     * hence one implicit (autocommit) transaction on one connection and one round trip.
     *
     * <p>The transaction-scoped advisory lock serialises appenders across every server, and is
     * released only after the commit is visible, so the next appender's {@code nextval} is both
     * higher and committed later: sequence order equals commit order, which is what lets CPES
     * consumers read without a fetch delay. It cannot outlive a dead session (G3).
     *
     * <p>Evaluation order is data-driven: the INSERT projects {@code nextval} from the single
     * row of the {@code es_lock} CTE, so the lock is taken before the number is drawn. The CTE
     * is materialised on every supported PostgreSQL without the {@code MATERIALIZED} keyword
     * (which only exists from 12): before 12 every CTE is, and from 12 on a CTE is inlined only
     * when it has no volatile function, while {@code pg_advisory_xact_lock} is volatile.
     * {@code EventStorePostgresIT} pins the order (lock before {@code nextval}). The key {@code hashtext('es-lock')} is the one the previous session-lock
     * implementation used, so during a rolling upgrade old and new servers still exclude each
     * other (session and transaction advisory locks share one lock space).
     *
     * <p>Round trips per append (measured by {@code EventStorePostgresIT}): before, 4 — a
     * session {@code pg_advisory_lock} on a dedicated connection, {@code SELECT nextval}, the
     * {@code INSERT}, {@code pg_advisory_unlock} (5 when the lock connection was re-validated);
     * now 1.
     */
    static final String CPES_APPEND_SQL =
            "WITH es_lock AS (SELECT pg_advisory_xact_lock(hashtext('es-lock'))) " +
                    "INSERT INTO es__events " +
                    "(event_sequence_number, aggregate_id, event_message, event_name, context) " +
                    "SELECT nextval('event_sequence_number_serial'), ?, ?, ?, ? FROM es_lock " +
                    "RETURNING event_sequence_number";

    private final long DELAY;
    private final EventStoreMode MODE;
    private final EventStoreRepository eventStoreRepository;
    private final SnapshotRepository snapshotRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper mapper = ObjectMapperUtils.getPayloadObjectMapper();
    private final Snowflake snowflake = new Snowflake();

    /** A cached {@code null} value means "this aggregate has no snapshot" (saves a lookup). */
    private final ExpiringLruCache<String, Snapshot> snapshotCache;
    /** Cached lists are never mutated after being cached: readers copy before appending. */
    private final ExpiringLruCache<String, List<EventStoreEntry>> eventsCache;


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

        if (aggregateIdentifier != null && !aggregateIdentifier.isBlank()) {
            predicates.add(
                    (r, o, cb) -> cb.equal(r.get("aggregateId"), aggregateIdentifier)
            );
        }

        if (eventName != null && !eventName.isBlank()) {
            predicates.add(
                    (r, o, cb) -> cb.equal(r.get("eventName"), eventName)
            );
        }

        if (context != null && !context.isBlank()) {
            predicates.add(
                    (r, o, cb) -> cb.equal(r.get("context"), context)
            );
        }

        if (eventSequenceNumber != null) {
            predicates.add(
                    (r, o, cb) -> cb.equal(r.get("eventSequenceNumber"), eventSequenceNumber)
            );
        }

        if (createdAtFrom != null) {
            predicates.add(
                    (r, o, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), createdAtTo)
            );
        }

        if (createdAtTo != null) {
            predicates.add(
                    (r, o, cb) -> cb.lessThanOrEqualTo(r.get("createdAt"), createdAtFrom)
            );
        }

        if (contentQuery != null && !contentQuery.isBlank()) {
            predicates.add(
                    (r, o, cb) -> cb.like(r.get("eventMessage"), contentQuery)
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
                                          String sortBy) {
        var s = new Snapshot();
        if (aggregateId != null && !aggregateId.isBlank())
            s.setAggregateId(aggregateId);

        return snapshotRepository.findAll(Example.of(s), PageRequest.of(
                page, size, Sort.by(sort, sortBy)
        ));
    }


    /**
     * Turns a consumer's context filter into the LIKE pattern the fetch queries match with
     * {@code ESCAPE '!'}: {@code *} is the only wildcard. LIKE's own metacharacters are escaped
     * first, because context names legitimately contain {@code _} ({@code SUD_AMERICA},
     * {@code AUSTRALIA_NZ}) and an unescaped {@code _} matches any character, so a
     * {@code SUD_AMERICA} consumer would also receive {@code SUDXAMERICA}'s events.
     */
    static String contextLikePattern(String context) {
        var sb = new StringBuilder(context.length() + 8);
        for (int i = 0; i < context.length(); i++) {
            char c = context.charAt(i);
            switch (c) {
                case '!', '%', '_' -> sb.append('!').append(c);
                case '*' -> sb.append('%');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public List<EventStoreEntry> fetchEvents(String context, Long seq, int limit) {
        if (seq == null) seq = -1L;
        if (MODE == EventStoreMode.CPES) {
            return eventStoreRepository.fetchEvents(
                    contextLikePattern(context),
                    seq, PageRequest.of(0, limit));
        }
        return eventStoreRepository.fetchEvents(
                contextLikePattern(context),
                seq,
                snowflake.forInstant(Instant.now().minus(DELAY, ChronoUnit.MILLIS)), PageRequest.of(0, limit));
    }

    public List<EventStoreEntry> fetchEvents(String context, Long seq, int limit, List<String> eventNames) {
        if (seq == null) seq = -1L;
        if (MODE == EventStoreMode.CPES) {
            return eventStoreRepository.fetchEvents(
                    contextLikePattern(context),
                    seq,
                    eventNames, PageRequest.of(0, limit));
        }
        return eventStoreRepository.fetchEvents(
                contextLikePattern(context),
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

        var id = new AtomicLong(snowflake.nextId());
        try {
            if (MODE == EventStoreMode.APES) {
                jdbcTemplate.update(
                        "INSERT INTO es__events " +
                                "(event_sequence_number," +
                                "aggregate_id, event_message, event_name, context) " +
                                "values  (?, ?, ?, ?, ?)",
                        id.get(),
                        aggregateId,
                        mapper.writeValueAsString(eventMessage),
                        eventMessage.getEventName(),
                        eventMessage.getContext()
                );
            } else {
                Long seq = jdbcTemplate.queryForObject(
                        CPES_APPEND_SQL,
                        Long.class,
                        aggregateId,
                        mapper.writeValueAsString(eventMessage),
                        eventMessage.getEventName(),
                        eventMessage.getContext()
                );
                if (seq == null) {
                    throw new IllegalStateException("Failed to retrieve next event sequence number.");
                }
                id.set(seq);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return id.get();
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
     * @param aggregateId                 The ID of the aggregate.
     * @param invalidateAggregateCaches   A flag indicating whether to invalidate the aggregate caches.
     * @param invalidateAggregateSnapshot A flag indicating whether to invalidate the aggregate snapshot.
     * @return The aggregate story, consisting of the serialized aggregate state and a list of domain events.
     */
    public AggregateStory fetchAggregateStory(String aggregateId,
                                              boolean invalidateAggregateCaches,
                                              boolean invalidateAggregateSnapshot) {
        Assert.isTrue(aggregateId != null, "Fetching aggregate state without an aggregate Id");
        if (invalidateAggregateCaches) {
            snapshotCache.remove(aggregateId);
            eventsCache.remove(aggregateId);
        }
        // One TTL-aware lookup per cache: a containsKey/get pair could straddle the expiry and
        // read a cached "no snapshot" for an aggregate that has one.
        Snapshot snapshot = null;
        if (!invalidateAggregateSnapshot) {
            var cached = snapshotCache.lookup(aggregateId);
            if (cached != null) {
                snapshot = cached.getValue();
            } else {
                snapshot = snapshotRepository.findById(aggregateId).orElse(null);
                snapshotCache.put(aggregateId, snapshot);
            }
        }
        // Copy: the cached list may be read concurrently by another command for the same
        // aggregate, so it is never appended to in place.
        var cachedEvents = eventsCache.get(aggregateId);
        var events = cachedEvents == null ? new ArrayList<EventStoreEntry>() : new ArrayList<>(cachedEvents);
        SqlRowSet rs;
        var max = 0L;
        var min = events.isEmpty() ? 0L : events.getFirst().getEventSequenceNumber();
        if (snapshot == null) {
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

        } else {
            max = events.isEmpty() ? snapshot.getEventSequenceNumber() : events.getLast().getEventSequenceNumber();
            if (snapshot.getEventSequenceNumber() < min) {
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

            } else {
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
        final Snapshot base = snapshot;
        return new AggregateStory(
                base == null ? new SerializedAggregateState<>(null) : base.getAggregateState(),
                events.stream()
                        .filter(e -> base == null || e.getEventSequenceNumber() > base.getEventSequenceNumber())
                        .map(e -> (DomainEventMessage) e.getEventMessage())
                        .toList()
        );
    }
}
