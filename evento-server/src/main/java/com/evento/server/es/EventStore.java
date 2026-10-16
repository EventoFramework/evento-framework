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
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
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
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class EventStore implements BrokerEventStore {

    private static final Logger logger = LoggerFactory.getLogger(EventStore.class);

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

    /** Name of the partial unique index on {@code es__events.idempotency_key} (G5). */
    public static final String IDEMPOTENCY_KEY_INDEX =
            com.evento.server.config.AdvisoryLockedSchemaInitializer.ES_EVENTS_IDEMPOTENCY_INDEX;

    /**
     * Arbiter of a keyed append. {@code ON CONFLICT ... DO NOTHING} rather than a caught unique
     * violation: when the conflicting row belongs to a transaction still in flight, PostgreSQL
     * waits for it, so an empty result means "a committed event already holds this key", which
     * is what the broker then reads back and replays. The partial predicate names the partial
     * index as arbiter.
     */
    private static final String IDEMPOTENCY_CONFLICT =
            " ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING";

    /**
     * {@link #CPES_APPEND_SQL} for a keyed command: the same single statement, two more columns
     * (the key and the command's type, its identity together with the aggregate id).
     */
    static final String CPES_KEYED_APPEND_SQL =
            "WITH es_lock AS (SELECT pg_advisory_xact_lock(hashtext('es-lock'))) " +
                    "INSERT INTO es__events " +
                    "(event_sequence_number, aggregate_id, event_message, event_name, context, idempotency_key, " +
                    "idempotency_command) " +
                    "SELECT nextval('event_sequence_number_serial'), ?, ?, ?, ?, ?, ? FROM es_lock" +
                    IDEMPOTENCY_CONFLICT +
                    " RETURNING event_sequence_number";

    static final String APES_KEYED_APPEND_SQL =
            "INSERT INTO es__events " +
                    "(event_sequence_number, aggregate_id, event_message, event_name, context, idempotency_key, " +
                    "idempotency_command) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)" + IDEMPOTENCY_CONFLICT;

    /** Claims table of in-flight (and event-less completed) keyed service commands. */
    public static final String IDEMPOTENCY_CLAIMS_TABLE =
            com.evento.server.config.AdvisoryLockedSchemaInitializer.IDEMPOTENCY_CLAIMS_TABLE;

    /**
     * Everything recorded for a key in ONE statement, so the event and the claim are read from
     * one snapshot: an owner stores its event before deleting its claim, so no reader can see
     * neither. The two lookups use the partial unique index and the claims primary key.
     */
    private static final String IDEMPOTENCY_RECORD_SQL =
            "SELECT e.event_sequence_number, e.event_message, e.idempotency_command, e.aggregate_id, " +
                    "c.command_type, c.aggregate_id, c.completed_at IS NOT NULL, c.lease_until < now(), " +
                    "c.idempotency_key IS NOT NULL " +
                    "FROM (SELECT 1) q " +
                    "LEFT JOIN es__events e ON e.idempotency_key = ? " +
                    "LEFT JOIN " + IDEMPOTENCY_CLAIMS_TABLE + " c ON c.idempotency_key = ?";

    /**
     * Claim a key: insert, or take over a claim whose lease ran out (its owner died), provided it
     * is not completed and names the same command. {@code ON CONFLICT DO UPDATE} locks the row,
     * so of two concurrent take-overs the second re-evaluates the {@code WHERE} against the
     * first's new lease and gets no row. A row back means "the claim is ours".
     */
    private static final String CLAIM_SQL =
            "INSERT INTO " + IDEMPOTENCY_CLAIMS_TABLE +
                    " (idempotency_key, command_type, aggregate_id, owner, lease_until) " +
                    "VALUES (?, ?, ?, ?, now() + ? * interval '1 millisecond') " +
                    "ON CONFLICT (idempotency_key) DO UPDATE SET owner = EXCLUDED.owner, " +
                    "lease_until = EXCLUDED.lease_until, created_at = now() " +
                    "WHERE " + IDEMPOTENCY_CLAIMS_TABLE + ".completed_at IS NULL " +
                    "AND " + IDEMPOTENCY_CLAIMS_TABLE + ".lease_until < now() " +
                    "AND " + IDEMPOTENCY_CLAIMS_TABLE + ".command_type = EXCLUDED.command_type " +
                    "AND " + IDEMPOTENCY_CLAIMS_TABLE + ".aggregate_id IS NOT DISTINCT FROM EXCLUDED.aggregate_id " +
                    "RETURNING owner";

    /**
     * The first attempt of a keyed service command, in ONE round trip (no lookup before it, no
     * re-check after it): what is recorded for the key, read from the statement's snapshot, and
     * a claim inserted (or taken over from a dead owner, see {@link #CLAIM_SQL}) only when no
     * event holds the key in that snapshot.
     *
     * <p>Why no re-check is needed: an owner that stores its event marks its claim completed in
     * the same statement ({@link #completingClaim}) and never deletes it. So a claim row exists,
     * in its latest version, for every key whose service event is stored. The insert's arbiter
     * reads that latest version (not the snapshot) and refuses a completed claim, even when the
     * event committed after this statement's snapshot was taken. Before, the owner deleted its
     * claim after the append: a claimer whose snapshot predated the append could then insert a
     * fresh claim and miss the event, hence the second lookup under the claim.
     *
     * <p>Columns: 1 the claim owner when this statement claimed, else null; 2–5 the event; 6–10
     * the claim as in {@link #IDEMPOTENCY_RECORD_SQL}. Parameters: key, key, then
     * {@link #CLAIM_SQL}'s five values.
     */
    static final String CLAIM_OR_FIND_SQL =
            "WITH ev AS (SELECT event_sequence_number, event_message, idempotency_command, aggregate_id " +
                    "FROM es__events WHERE idempotency_key = ?), " +
                    "cl AS (SELECT command_type, aggregate_id, completed_at IS NOT NULL AS completed, " +
                    "lease_until < now() AS expired FROM " + IDEMPOTENCY_CLAIMS_TABLE + " WHERE idempotency_key = ?), " +
                    "ins AS (INSERT INTO " + IDEMPOTENCY_CLAIMS_TABLE +
                    " (idempotency_key, command_type, aggregate_id, owner, lease_until) " +
                    "SELECT ?, ?, ?, ?, now() + ? * interval '1 millisecond' WHERE NOT EXISTS (SELECT 1 FROM ev) " +
                    "ON CONFLICT (idempotency_key) DO UPDATE SET owner = EXCLUDED.owner, " +
                    "lease_until = EXCLUDED.lease_until, created_at = now() " +
                    "WHERE " + IDEMPOTENCY_CLAIMS_TABLE + ".completed_at IS NULL " +
                    "AND " + IDEMPOTENCY_CLAIMS_TABLE + ".lease_until < now() " +
                    "AND " + IDEMPOTENCY_CLAIMS_TABLE + ".command_type = EXCLUDED.command_type " +
                    "AND " + IDEMPOTENCY_CLAIMS_TABLE + ".aggregate_id IS NOT DISTINCT FROM EXCLUDED.aggregate_id " +
                    "RETURNING owner) " +
                    "SELECT (SELECT owner FROM ins), ev.event_sequence_number, ev.event_message, " +
                    "ev.idempotency_command, ev.aggregate_id, cl.command_type, cl.aggregate_id, cl.completed, " +
                    "cl.expired, cl.command_type IS NOT NULL " +
                    "FROM (SELECT 1) q LEFT JOIN ev ON true LEFT JOIN cl ON true";

    /**
     * Turns a keyed append statement (one that ends with {@code RETURNING event_sequence_number})
     * into one that also marks the service command's claim completed, in the same statement and
     * so the same transaction: the stored event and the completed claim become visible together,
     * and the claim row stays (see {@link #CLAIM_OR_FIND_SQL}). Two more parameters: key, owner.
     * The claim of another owner (one that took the key over) is left alone.
     */
    static String completingClaim(String keyedAppendReturningSeq) {
        return "WITH appended AS (" + keyedAppendReturningSeq + "), " +
                "done AS (UPDATE " + IDEMPOTENCY_CLAIMS_TABLE + " SET completed_at = now() " +
                "WHERE idempotency_key = ? AND owner = ? AND completed_at IS NULL " +
                "AND EXISTS (SELECT 1 FROM appended)) " +
                "SELECT event_sequence_number FROM appended";
    }

    /**
     * Columns appended to an aggregate-story query so the key lookup of a keyed aggregate command
     * rides on the story fetch (one round trip): the story rows get kind 0 and nulls, the record
     * row kind 1 and {@link #IDEMPOTENCY_RECORD_SQL}'s columns. Two more parameters: key, key.
     */
    private static final String STORY_ROW_RECORD_COLUMNS =
            ", null::varchar, null::varchar, null::varchar, null::varchar, null::boolean, null::boolean, " +
                    "null::boolean ";

    private static final String STORY_RECORD_ROW =
            " UNION ALL SELECT 1, e.event_sequence_number, e.event_message, e.idempotency_command, e.aggregate_id, " +
                    "c.command_type, c.aggregate_id, c.completed_at IS NOT NULL, c.lease_until < now(), " +
                    "c.idempotency_key IS NOT NULL " +
                    "FROM (SELECT 1) q " +
                    "LEFT JOIN es__events e ON e.idempotency_key = ? " +
                    "LEFT JOIN " + IDEMPOTENCY_CLAIMS_TABLE + " c ON c.idempotency_key = ? " +
                    "ORDER BY 1, 2";

    /** SQLSTATEs meaning "the schema lacks what keys need" (dropped index, column or table). */
    private static final java.util.Set<String> MISSING_SCHEMA_STATES = java.util.Set.of(
            "42P10", // no unique index matching the ON CONFLICT arbiter
            "42703", // undefined column
            "42P01"  // undefined table
    );

    /** How long a "no idempotency support" answer is trusted before the catalog is asked again. */
    private static final long IDEMPOTENCY_PROBE_INTERVAL_MS = 60_000;
    private volatile boolean idempotencySupported;
    private volatile long idempotencyProbeAt;
    /** {@code null} until known; {@code false} on a database other than PostgreSQL (for good). */
    private volatile Boolean postgres;

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

    /**
     * A keyed append (G5): the event, its command's idempotency key and type in one statement,
     * under the partial unique index, so no two servers can both store one key. An unkeyed
     * append, or a keyed one without support for keys (see {@link #idempotencySupported()}), is
     * the plain {@link #publishEvent(EventMessage, String)}. A schema that lost the index or the
     * column since it was probed ({@code 42P10}, {@code 42703}) turns support off, with a
     * warning, and the event is appended unkeyed rather than not at all.
     */
    @Override
    public long publishEvent(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey,
                             String commandType) {
        return publishKeyed(eventMessage, aggregateId, idempotencyKey, commandType, null);
    }

    /**
     * The keyed append of a service command: the event, and its claim marked completed, in one
     * statement (one round trip, one transaction). The claim is kept, not deleted: see
     * {@link #CLAIM_OR_FIND_SQL} for why that lets the claim of a retry need no second lookup.
     */
    @Override
    public long publishEvent(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey,
                             String commandType, String claimOwner) {
        return publishKeyed(eventMessage, aggregateId, idempotencyKey, commandType, claimOwner);
    }

    private long publishKeyed(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey,
                              String commandType, String claimOwner) {
        if (idempotencyKey == null || !idempotencySupported()) {
            return publishEvent(eventMessage, aggregateId);
        }
        String json;
        try {
            json = mapper.writeValueAsString(eventMessage);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        try {
            if (MODE == EventStoreMode.APES) {
                long id = snowflake.nextId();
                if (claimOwner == null) {
                    int inserted = jdbcTemplate.update(APES_KEYED_APPEND_SQL, id, aggregateId, json,
                            eventMessage.getEventName(), eventMessage.getContext(), idempotencyKey, commandType);
                    if (inserted == 0) throw new IdempotencyKeyConflictException(idempotencyKey);
                    return id;
                }
                List<Long> seq = jdbcTemplate.queryForList(
                        completingClaim(APES_KEYED_APPEND_SQL + " RETURNING event_sequence_number"), Long.class,
                        id, aggregateId, json, eventMessage.getEventName(), eventMessage.getContext(),
                        idempotencyKey, commandType, idempotencyKey, claimOwner);
                if (seq.isEmpty()) throw new IdempotencyKeyConflictException(idempotencyKey);
                return id;
            }
            List<Long> seq = claimOwner == null
                    ? jdbcTemplate.queryForList(CPES_KEYED_APPEND_SQL, Long.class, aggregateId, json,
                    eventMessage.getEventName(), eventMessage.getContext(), idempotencyKey, commandType)
                    : jdbcTemplate.queryForList(completingClaim(CPES_KEYED_APPEND_SQL), Long.class, aggregateId, json,
                    eventMessage.getEventName(), eventMessage.getContext(), idempotencyKey, commandType,
                    idempotencyKey, claimOwner);
            if (seq.isEmpty()) throw new IdempotencyKeyConflictException(idempotencyKey);
            return seq.getFirst();
        } catch (DataAccessException e) {
            if (!schemaLost(e, "keyed_append")) throw e;
            return publishEvent(eventMessage, aggregateId);
        }
    }

    /**
     * Claims the key or reports what is recorded for it, in one round trip
     * ({@link #CLAIM_OR_FIND_SQL}).
     */
    @Override
    public ClaimAttempt claimOrFindIdempotencyKey(String idempotencyKey, String commandType, String aggregateId,
                                                  String owner, long leaseMs) {
        try {
            return jdbcTemplate.query(CLAIM_OR_FIND_SQL, rs -> {
                if (!rs.next()) return new ClaimAttempt(false, null);
                String claimedBy = rs.getString(1);
                if (owner.equals(claimedBy)) return new ClaimAttempt(true, null);
                return new ClaimAttempt(false, readRecord(rs, 2));
            }, idempotencyKey, idempotencyKey, idempotencyKey, commandType, aggregateId, owner, Math.max(1L, leaseMs));
        } catch (DataAccessException e) {
            // Claims table gone: run as an unkeyed command would (support is now off).
            if (!schemaLost(e, "claim")) throw e;
            return new ClaimAttempt(true, null);
        }
    }

    /**
     * Reads the record columns starting at {@code first}: event seq, message, command, aggregate,
     * then claim type, aggregate, completed, expired, present (as {@link #IDEMPOTENCY_RECORD_SQL}).
     */
    private IdempotencyRecord readRecord(java.sql.ResultSet rs, int first) throws SQLException {
        StoredEvent event = null;
        long seq = rs.getLong(first);
        if (!rs.wasNull()) {
            try {
                event = new StoredEvent(seq, (EventMessage<?>) mapper.readValue(rs.getString(first + 1), Serializable.class));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("stored event " + seq + " is unreadable", e);
            }
        }
        boolean claimed = rs.getBoolean(first + 8);
        ClaimState claim = !claimed ? ClaimState.NONE
                : rs.getBoolean(first + 6) ? ClaimState.COMPLETED
                : rs.getBoolean(first + 7) ? ClaimState.EXPIRED
                : ClaimState.RUNNING;
        if (event != null) {
            return new IdempotencyRecord(rs.getString(first + 2), rs.getString(first + 3), event, claim);
        }
        if (claimed) {
            return new IdempotencyRecord(rs.getString(first + 4), rs.getString(first + 5), null, claim);
        }
        return null;
    }

    /**
     * The event and the claim recorded for {@code idempotencyKey}. Also finds an event of a
     * deleted aggregate: the command did run, and a retry must learn that rather than run it
     * again.
     */
    @Override
    public Optional<IdempotencyRecord> findIdempotencyRecord(String idempotencyKey) {
        if (idempotencyKey == null || !idempotencySupported()) return Optional.empty();
        try {
            return jdbcTemplate.query(IDEMPOTENCY_RECORD_SQL,
                    rs -> rs.next() ? Optional.ofNullable(readRecord(rs, 1)) : Optional.<IdempotencyRecord>empty(),
                    idempotencyKey, idempotencyKey);
        } catch (DataAccessException e) {
            if (!schemaLost(e, "lookup")) throw e;
            return Optional.empty();
        }
    }

    @Override
    public boolean claimIdempotencyKey(String idempotencyKey, String commandType, String aggregateId,
                                       String owner, long leaseMs) {
        try {
            return !jdbcTemplate.queryForList(CLAIM_SQL, String.class,
                    idempotencyKey, commandType, aggregateId, owner, Math.max(1L, leaseMs)).isEmpty();
        } catch (DataAccessException e) {
            // Claims table gone: run as an unkeyed command would (support is now off).
            if (!schemaLost(e, "claim")) throw e;
            return true;
        }
    }

    @Override
    public boolean renewIdempotencyClaim(String idempotencyKey, String owner, long leaseMs) {
        return jdbcTemplate.update("UPDATE " + IDEMPOTENCY_CLAIMS_TABLE +
                        " SET lease_until = now() + ? * interval '1 millisecond' " +
                        "WHERE idempotency_key = ? AND owner = ? AND completed_at IS NULL",
                Math.max(1L, leaseMs), idempotencyKey, owner) > 0;
    }

    @Override
    public boolean completeIdempotencyClaim(String idempotencyKey, String owner) {
        return jdbcTemplate.update("UPDATE " + IDEMPOTENCY_CLAIMS_TABLE + " SET completed_at = now() " +
                "WHERE idempotency_key = ? AND owner = ? AND completed_at IS NULL", idempotencyKey, owner) > 0;
    }

    @Override
    public void releaseIdempotencyClaim(String idempotencyKey, String owner) {
        jdbcTemplate.update("DELETE FROM " + IDEMPOTENCY_CLAIMS_TABLE +
                " WHERE idempotency_key = ? AND owner = ? AND completed_at IS NULL", idempotencyKey, owner);
    }

    /**
     * Whether {@code e} says the schema no longer has what keys need; if so, support is turned
     * off (re-probed after {@value #IDEMPOTENCY_PROBE_INTERVAL_MS} ms) with a warning.
     */
    private boolean schemaLost(DataAccessException e, String operation) {
        var state = sqlState(e);
        if (state == null || !MISSING_SCHEMA_STATES.contains(state)) return false;
        synchronized (this) {
            idempotencySupported = false;
            idempotencyProbeAt = System.currentTimeMillis() + IDEMPOTENCY_PROBE_INTERVAL_MS;
        }
        logger.warn("event=idempotency_key_unsupported operation={} sqlState={} reason=\"the idempotency schema "
                        + "(index {}, column idempotency_command, table {}) is gone; keyed commands run without "
                        + "exactly-once until it is back\" error=\"{}\"",
                operation, state, IDEMPOTENCY_KEY_INDEX, IDEMPOTENCY_CLAIMS_TABLE, e.getMostSpecificCause().getMessage());
        return true;
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) return sql.getSQLState();
        }
        return null;
    }

    /**
     * Whether keys can be enforced: a PostgreSQL database whose {@code es__events} (the table the
     * append statements resolve through {@code search_path}) has a valid
     * {@value #IDEMPOTENCY_KEY_INDEX} and an {@code idempotency_command} column, and whose
     * {@code search_path} resolves {@value #IDEMPOTENCY_CLAIMS_TABLE}. Without them keyed
     * commands run unkeyed (the pre-G5 behaviour) and this logs
     * {@code event=idempotency_key_unsupported}: a schema managed outside the server
     * ({@code spring.sql.init.mode=never}) that has not applied
     * {@code db/upgrade/es-events-idempotency-key.sql} yet. A negative answer is re-checked
     * every minute, so applying the script takes effect without a restart; a database other than
     * PostgreSQL is never supported (the statements are PostgreSQL's).
     */
    @Override
    public boolean idempotencySupported() {
        if (idempotencySupported) return true;
        if (Boolean.FALSE.equals(postgres)) return false;
        long now = System.currentTimeMillis();
        if (now < idempotencyProbeAt) return false;
        synchronized (this) {
            if (idempotencySupported) return true;
            if (now < idempotencyProbeAt) return false;
            if (postgres == null) {
                String product = jdbcTemplate.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
                postgres = product != null && product.toLowerCase(java.util.Locale.ROOT).contains("postgresql");
                if (!postgres) {
                    logger.warn("event=idempotency_key_unsupported database=\"{}\" reason=\"idempotency keys need "
                            + "PostgreSQL; keyed commands run without exactly-once\"", product);
                    return false;
                }
            }
            Boolean ready = jdbcTemplate.queryForObject(
                    "SELECT coalesce((SELECT i.indisvalid AND i.indisunique FROM pg_index i " +
                            "JOIN pg_class c ON c.oid = i.indexrelid " +
                            "WHERE i.indrelid = to_regclass('es__events') AND c.relname = ?), false) " +
                            "AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = to_regclass('es__events') " +
                            "AND attname = 'idempotency_command' AND NOT attisdropped) " +
                            "AND to_regclass(?) IS NOT NULL",
                    Boolean.class, IDEMPOTENCY_KEY_INDEX, IDEMPOTENCY_CLAIMS_TABLE);
            idempotencySupported = Boolean.TRUE.equals(ready);
            if (!idempotencySupported) {
                idempotencyProbeAt = now + IDEMPOTENCY_PROBE_INTERVAL_MS;
                logger.warn("event=idempotency_key_unsupported index={} table={} reason=\"no valid index, "
                        + "idempotency_command column or claims table; keyed commands run without exactly-once "
                        + "until they exist\" script=classpath:db/upgrade/es-events-idempotency-key.sql",
                        IDEMPOTENCY_KEY_INDEX, IDEMPOTENCY_CLAIMS_TABLE);
            }
            return idempotencySupported;
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
        return fetchStory(aggregateId, invalidateAggregateCaches, invalidateAggregateSnapshot, null).story();
    }

    /**
     * The story and, for a keyed command, what is recorded for its key, in the story's own round
     * trip: the key lookup is a {@code UNION ALL} branch of the events query
     * ({@link #STORY_RECORD_ROW}), so a keyed aggregate command costs exactly the round trips of
     * an unkeyed one. Before, it paid two more lookups (one before the aggregate lock, one under
     * it), each a round trip to the event-store primary: from a remote cell, one or two WAN RTTs
     * per command.
     */
    @Override
    public KeyedAggregateStory fetchAggregateStory(String aggregateId, boolean invalidateCaches,
                                                   boolean invalidateSnapshot, String idempotencyKey) {
        if (idempotencyKey == null || !idempotencySupported()) {
            return fetchStory(aggregateId, invalidateCaches, invalidateSnapshot, null);
        }
        try {
            return fetchStory(aggregateId, invalidateCaches, invalidateSnapshot, idempotencyKey);
        } catch (DataAccessException e) {
            if (!schemaLost(e, "story_lookup")) throw e;
            return fetchStory(aggregateId, invalidateCaches, invalidateSnapshot, null);
        }
    }

    private KeyedAggregateStory fetchStory(String aggregateId,
                                           boolean invalidateAggregateCaches,
                                           boolean invalidateAggregateSnapshot,
                                           String idempotencyKey) {
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
        String where;
        Object[] params;
        long max;
        var min = events.isEmpty() ? 0L : events.getFirst().getEventSequenceNumber();
        if (snapshot == null) {
            max = events.isEmpty() ? 0L : events.getLast().getEventSequenceNumber();
            where = "where aggregate_id = ? " +
                    "and (es__events.event_sequence_number < ? or es__events.event_sequence_number > ?) ";
            params = new Object[]{aggregateId, min, max};
        } else {
            max = events.isEmpty() ? snapshot.getEventSequenceNumber() : events.getLast().getEventSequenceNumber();
            if (snapshot.getEventSequenceNumber() < min) {
                where = "where aggregate_id = ? " +
                        "and ((es__events.event_sequence_number > ? and es__events.event_sequence_number < ?) " +
                        "or (es__events.event_sequence_number > ?)) ";
                params = new Object[]{aggregateId, snapshot.getEventSequenceNumber(),
                        events.getFirst().getEventSequenceNumber(), max};
            } else {
                where = "where aggregate_id = ? and es__events.event_sequence_number > ? ";
                params = new Object[]{aggregateId, max};
            }
        }
        String sql;
        if (idempotencyKey == null) {
            sql = "select 0, event_sequence_number, event_message from es__events " + where
                    + "order by event_sequence_number";
        } else {
            sql = "select 0, event_sequence_number, event_message" + STORY_ROW_RECORD_COLUMNS
                    + "from es__events " + where + STORY_RECORD_ROW;
            params = java.util.Arrays.copyOf(params, params.length + 2);
            params[params.length - 2] = idempotencyKey;
            params[params.length - 1] = idempotencyKey;
        }

        final long fetchedAfter = max;
        var record = new IdempotencyRecord[1];
        var inserted = new int[1];
        jdbcTemplate.query(sql, (java.sql.ResultSet rs) -> {
            if (rs.getInt(1) == 1) {
                record[0] = readRecord(rs, 2);
                return;
            }
            try {
                var entry = new EventStoreEntry();
                entry.setEventSequenceNumber(rs.getLong(2));
                entry.setEventMessage(mapper.readValue(rs.getString(3), DomainEventMessage.class));
                if (entry.getEventSequenceNumber() > fetchedAfter) {
                    events.add(entry);
                } else {
                    events.add(inserted[0]++, entry);
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }, params);
        eventsCache.put(aggregateId, events);
        final Snapshot base = snapshot;
        return new KeyedAggregateStory(new AggregateStory(
                base == null ? new SerializedAggregateState<>(null) : base.getAggregateState(),
                events.stream()
                        .filter(e -> base == null || e.getEventSequenceNumber() > base.getEventSequenceNumber())
                        .map(e -> (DomainEventMessage) e.getEventMessage())
                        .toList()
        ), record[0]);
    }
}
