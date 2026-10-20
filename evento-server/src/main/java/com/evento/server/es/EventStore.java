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
public class EventStore implements BrokerEventStore, AutoCloseable {

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

    private static final String ES_LOCK_CTE = "WITH es_lock AS (SELECT pg_advisory_xact_lock(hashtext('es-lock'))) ";

    /**
     * A CPES single-statement append whose wait for {@code es-lock} is bounded by the current
     * {@link AppendDeadline} (N15): {@code sql} with a first parameter, the statement's
     * {@code lock_timeout}, and {@code args} after it. Without a deadline, {@code sql} as it is.
     */
    private <T> List<T> cpesAppend(String sql, Class<T> type, Object... args) {
        long deadline = AppendDeadline.current();
        if (deadline == Long.MAX_VALUE) return jdbcTemplate.queryForList(sql, type, args);
        if (AppendDeadline.passed(deadline)) throw AppendDeadline.expired("before its append");
        if (!sql.startsWith(ES_LOCK_CTE)) throw new IllegalStateException("not a CPES append: " + sql);
        String bounded = "WITH cfg AS MATERIALIZED (SELECT set_config('lock_timeout', ?, true)), "
                + "es_lock AS (SELECT pg_advisory_xact_lock(hashtext('es-lock')) FROM cfg) "
                + sql.substring(ES_LOCK_CTE.length());
        var all = new Object[args.length + 1];
        all[0] = AppendDeadline.lockTimeout(deadline);
        System.arraycopy(args, 0, all, 1, args.length);
        try {
            return jdbcTemplate.queryForList(bounded, type, all);
        } catch (RuntimeException e) {
            if (AppendDeadline.lockTimedOut(e)) throw AppendDeadline.expired("while it waited for es-lock");
            throw e;
        }
    }

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

    /** Name of the partial unique index on {@code es__events (aggregate_id, aggregate_seq)} (C1). */
    public static final String AGGREGATE_SEQ_INDEX =
            com.evento.server.config.AdvisoryLockedSchemaInitializer.ES_EVENTS_AGGREGATE_SEQ_INDEX;

    /**
     * The fenced appends of aggregate events (C1): the statements above with one more column,
     * {@code aggregate_seq}, the aggregate's version after this event. A version already taken
     * violates {@link #AGGREGATE_SEQ_INDEX}, which is not an {@code ON CONFLICT} arbiter: the
     * statement fails (23505) and nothing is stored. Under CPES the writer that took the version
     * committed before this one got {@code es-lock}, so the check never waits; under APES it waits
     * for a writer still in flight and then fails if that one committed.
     */
    static final String CPES_FENCED_APPEND_SQL =
            "WITH es_lock AS (SELECT pg_advisory_xact_lock(hashtext('es-lock'))) " +
                    "INSERT INTO es__events " +
                    "(event_sequence_number, aggregate_id, event_message, event_name, context, aggregate_seq) " +
                    "SELECT nextval('event_sequence_number_serial'), ?, ?, ?, ?, ? FROM es_lock " +
                    "RETURNING event_sequence_number";

    static final String CPES_FENCED_KEYED_APPEND_SQL =
            "WITH es_lock AS (SELECT pg_advisory_xact_lock(hashtext('es-lock'))) " +
                    "INSERT INTO es__events " +
                    "(event_sequence_number, aggregate_id, event_message, event_name, context, aggregate_seq, " +
                    "idempotency_key, idempotency_command) " +
                    "SELECT nextval('event_sequence_number_serial'), ?, ?, ?, ?, ?, ?, ? FROM es_lock" +
                    IDEMPOTENCY_CONFLICT +
                    " RETURNING event_sequence_number";

    static final String APES_FENCED_APPEND_SQL =
            "INSERT INTO es__events " +
                    "(event_sequence_number, aggregate_id, event_message, event_name, context, aggregate_seq) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

    static final String APES_FENCED_KEYED_APPEND_SQL =
            "INSERT INTO es__events " +
                    "(event_sequence_number, aggregate_id, event_message, event_name, context, aggregate_seq, " +
                    "idempotency_key, idempotency_command) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)" + IDEMPOTENCY_CONFLICT;

    /**
     * The story's version row (kind 2), a branch of the story query, so the version is read in
     * the same snapshot as the events and costs no round trip: the aggregate's newest fenced
     * event, its sequence number (column 2) and version (column 3, as text to fit the union).
     * One backward step of {@link #AGGREGATE_SEQ_INDEX}. One more parameter: the aggregate id.
     * The text column is aliased: unaliased it is still named {@code aggregate_seq}, and
     * {@code ORDER BY} prefers an output column to an input one, so the newest version was picked
     * as text ("9" after "10") and every append past version 10 was refused.
     */
    private static final String STORY_VERSION_ROW =
            " UNION ALL (SELECT 2, event_sequence_number, aggregate_seq::text AS version_text%s FROM es__events " +
                    "WHERE aggregate_id = ? AND aggregate_seq IS NOT NULL ORDER BY aggregate_seq DESC LIMIT 1)";

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
                    "LEFT JOIN " + IDEMPOTENCY_CLAIMS_TABLE + " c ON c.idempotency_key = ?";

    /** SQLSTATEs meaning "the schema lacks what keys need" (dropped index, column or table). */
    private static final java.util.Set<String> MISSING_SCHEMA_STATES = java.util.Set.of(
            "42P10", // no unique index matching the ON CONFLICT arbiter
            "42703", // undefined column
            "42P01"  // undefined table
    );

    /** How long a "no idempotency support" answer is trusted before the catalog is asked again. */
    private static final long IDEMPOTENCY_PROBE_INTERVAL_MS = 60_000;
    private static final String UNIQUE_VIOLATION = "23505";
    private volatile boolean idempotencySupported;
    private volatile long idempotencyProbeAt;
    private volatile boolean fenceSupported;
    private volatile long fenceProbeAt;
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

    /** Group commit of aggregate appends ({@link AppendBatcher}); 0 writers turns it off. */
    private volatile int appendWriters = -1;
    private volatile int appendMaxBatch = 256;
    private volatile AppendBatcher appendBatcher;


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
                      @Value("${evento.es.mode:CPES}")
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
            continueSequenceAboveApesIds();
        } else {
            logger.info("Mode: APES - Fetch Delay: {}", fetchDelay);
            logger.warn("event=es_mode_deprecated mode=APES reason=\"APES is deprecated since 3.0: with more than "
                    + "one server its consumers can skip events, and on one server CPES is as fast; an existing "
                    + "APES store switches to CPES by restarting every server with evento.es.mode=CPES\"");
        }
        logger.info("Aggregate Snapshot Cache Size: {} - TTL: {}", aggregateSnapshotCacheSize, aggregateEventsCacheExpiry);
        logger.info("Aggregate Story Cache Size: {} - TTL: {}", aggregateEventsCacheSize, aggregateEventsCacheExpiry);

    }


    /**
     * Group commit of aggregate appends ({@link AppendBatcher}).
     *
     * @param writers  batches written at once; 0 turns group commit off (one statement and one
     *                 commit per append, as before), a negative value picks the mode's default
     *                 (CPES 2: one commits while the next waits for {@code es-lock}; APES 4)
     * @param maxBatch the most appends in one batch
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void configureAppendBatching(@Value("${evento.es.append.batch.writers:-1}") int writers,
                                        @Value("${evento.es.append.batch.max-size:256}") int maxBatch) {
        this.appendWriters = writers;
        this.appendMaxBatch = maxBatch;
    }

    /**
     * Where consumers' fetches read (G21): a PostgreSQL replica close to the server, configured with
     * {@code evento.es.read.datasource.url} (and {@code .username}, {@code .password},
     * {@code .maximum-pool-size}, default 10). Under CPES a replica holds a prefix of the commits in
     * order and the sequence order is the commit order, so a consumer reading there never skips an
     * event: it only sees them later, by the replication lag. Aggregate stories, keys, appends and
     * the head stay on the primary (the fence must see the latest version). Under APES a replica is
     * not used: an event the replica has not replayed yet, behind the fetch delay, would be skipped.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void configureReadDataSource(@Value("${evento.es.read.datasource.url:}") String url,
                                        @Value("${evento.es.read.datasource.username:}") String username,
                                        @Value("${evento.es.read.datasource.password:}") String password,
                                        @Value("${evento.es.read.datasource.maximum-pool-size:10}") int poolSize) {
        if (url == null || url.isBlank()) return;
        if (MODE != EventStoreMode.CPES) {
            logger.warn("event=read_datasource_ignored mode={} reason=\"under APES a replica behind the fetch "
                    + "delay would make consumers skip events; fetches stay on the primary\"", MODE);
            return;
        }
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setPoolName("evento-es-read");
        config.setJdbcUrl(url);
        if (username != null && !username.isBlank()) config.setUsername(username);
        if (password != null && !password.isEmpty()) config.setPassword(password);
        config.setMaximumPoolSize(Math.max(1, poolSize));
        config.setMinimumIdle(Math.min(2, Math.max(1, poolSize)));
        config.setReadOnly(true);
        var pool = new com.zaxxer.hikari.HikariDataSource(config);
        this.readPool = pool;
        this.readJdbc = new JdbcTemplate(pool);
        logger.info("event=read_datasource_configured url={} poolSize={}", url, poolSize);
    }

    private volatile com.zaxxer.hikari.HikariDataSource readPool;
    private volatile JdbcTemplate readJdbc;

    private static final String READ_FETCH_SQL =
            "SELECT event_sequence_number, context, aggregate_id, created_at, event_message, event_name, deleted_at "
                    + "FROM es__events WHERE context LIKE ? ESCAPE '!' AND event_sequence_number > ? "
                    + "ORDER BY event_sequence_number LIMIT ?";
    private static final String READ_FETCH_NAMES_SQL =
            "SELECT event_sequence_number, context, aggregate_id, created_at, event_message, event_name, deleted_at "
                    + "FROM es__events WHERE context LIKE ? ESCAPE '!' AND event_sequence_number > ? "
                    + "AND event_name = ANY (?) ORDER BY event_sequence_number LIMIT ?";

    private static final com.evento.server.config.JsonConverter EVENT_JSON = new com.evento.server.config.JsonConverter();

    private static EventStoreEntry readEntry(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var e = new EventStoreEntry();
        e.setEventSequenceNumber(rs.getLong(1));
        e.setContext(rs.getString(2));
        e.setAggregateId(rs.getString(3));
        e.setCreatedAt(rs.getTimestamp(4));
        e.setEventMessage((com.evento.common.modeling.messaging.message.application.EventMessage<?>)
                EVENT_JSON.convertToEntityAttribute(rs.getString(5)));
        e.setEventName(rs.getString(6));
        e.setDeletedAt(rs.getTimestamp(7));
        return e;
    }

    /** Whether consumers' fetches read from a replica (G21). */
    public boolean readsFromReplica() {
        return readJdbc != null;
    }

    /** The running batcher, started on the first append; {@code null} when group commit is off. */
    private AppendBatcher appendBatcher() {
        var batcher = appendBatcher;
        if (batcher != null) return batcher;
        int writers = appendWriters < 0 ? (MODE == EventStoreMode.CPES ? 2 : 4) : appendWriters;
        if (writers == 0) return null;
        synchronized (this) {
            if (appendBatcher == null) {
                appendBatcher = new AppendBatcher(jdbcTemplate, MODE == EventStoreMode.CPES, snowflake::nextId,
                        writers, appendMaxBatch);
                logger.info("event=append_batching_started mode={} writers={} maxBatch={}", MODE, writers, appendMaxBatch);
            }
            return appendBatcher;
        }
    }

    /** Batches written by group commit (each one commit), for metrics and tests. */
    public long appendBatchCount() {
        var batcher = appendBatcher;
        return batcher == null ? 0 : batcher.batchCount();
    }

    /** Aggregate appends written by group commit. */
    public long batchedAppendCount() {
        var batcher = appendBatcher;
        return batcher == null ? 0 : batcher.appendCount();
    }

    @Override
    public void close() {
        var batcher = appendBatcher;
        if (batcher != null) batcher.close();
        var pool = readPool;
        if (pool != null) pool.close();
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
        var replica = readJdbc;
        if (replica != null) {
            return replica.query(READ_FETCH_SQL, EventStore::readEntry, contextLikePattern(context), seq, limit);
        }
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
        var replica = readJdbc;
        if (replica != null) {
            final long from = seq;
            return replica.execute((org.springframework.jdbc.core.ConnectionCallback<List<EventStoreEntry>>) c -> {
                try (var ps = c.prepareStatement(READ_FETCH_NAMES_SQL)) {
                    ps.setString(1, contextLikePattern(context));
                    ps.setLong(2, from);
                    ps.setArray(3, c.createArrayOf("text", eventNames.toArray()));
                    ps.setInt(4, limit);
                    var out = new java.util.ArrayList<EventStoreEntry>();
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) out.add(readEntry(rs, out.size()));
                    }
                    return out;
                }
            });
        }
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
                var appended = cpesAppend(
                        CPES_APPEND_SQL,
                        Long.class,
                        aggregateId,
                        mapper.writeValueAsString(eventMessage),
                        eventMessage.getEventName(),
                        eventMessage.getContext()
                );
                Long seq = appended.isEmpty() ? null : appended.getFirst();
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
                    ? cpesAppend(CPES_KEYED_APPEND_SQL, Long.class, aggregateId, json,
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
     * The fenced append of an aggregate event (C1): version {@code expectedVersion + 1}, and the
     * key when there is one, in the mode's single statement. Without the fence (a schema that
     * lacks it, no version read with the story) it is the plain keyed append. A schema that lost
     * the column or the idempotency index since it was probed turns that support off, and the
     * event is appended with what is left rather than not at all.
     */
    @Override
    public long appendAggregateEvent(EventMessage<?> eventMessage, String aggregateId, String idempotencyKey,
                                     String commandType, Long expectedVersion) {
        if (expectedVersion == null || aggregateId == null || !fenceSupported()) {
            return publishEvent(eventMessage, aggregateId, idempotencyKey, commandType);
        }
        boolean keyed = idempotencyKey != null && idempotencySupported();
        long version = expectedVersion + 1;
        String json;
        try {
            json = mapper.writeValueAsString(eventMessage);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        String name = eventMessage.getEventName();
        String context = eventMessage.getContext();
        // Group commit needs the key columns (on every schema that has the fence, unless an
        // externally managed one dropped them): without them, one statement per append.
        var batcher = idempotencySupported() ? appendBatcher() : null;
        if (batcher != null) {
            try {
                long seq = batcher.append(aggregateId, json, name, context, version,
                        keyed ? idempotencyKey : null, keyed ? commandType : null);
                if (seq >= 0) return seq;
                // Kept out by a conflicting row: its key, or else its version (the fence). A new
                // statement sees the row that won, committed (the batch waited for it).
                if (keyed && Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM es__events WHERE idempotency_key = ?)", Boolean.class,
                        idempotencyKey))) {
                    throw new IdempotencyKeyConflictException(idempotencyKey);
                }
                throw fenceConflict(aggregateId, expectedVersion, commandType);
            } catch (AppendBatcher.BatchFailedException failed) {
                // Not a conflict: append alone, where every schema fallback below applies.
            }
        }
        try {
            if (MODE == EventStoreMode.APES) {
                long id = snowflake.nextId();
                int inserted = keyed
                        ? jdbcTemplate.update(APES_FENCED_KEYED_APPEND_SQL, id, aggregateId, json, name, context, version,
                        idempotencyKey, commandType)
                        : jdbcTemplate.update(APES_FENCED_APPEND_SQL, id, aggregateId, json, name, context, version);
                if (inserted == 0) throw new IdempotencyKeyConflictException(idempotencyKey);
                return id;
            }
            List<Long> seq = keyed
                    ? cpesAppend(CPES_FENCED_KEYED_APPEND_SQL, Long.class, aggregateId, json, name,
                    context, version, idempotencyKey, commandType)
                    : cpesAppend(CPES_FENCED_APPEND_SQL, Long.class, aggregateId, json, name, context,
                    version);
            if (seq.isEmpty()) throw new IdempotencyKeyConflictException(idempotencyKey);
            return seq.getFirst();
        } catch (DataAccessException e) {
            if (UNIQUE_VIOLATION.equals(sqlState(e)) && String.valueOf(e.getMostSpecificCause().getMessage())
                    .contains(AGGREGATE_SEQ_INDEX)) {
                throw fenceConflict(aggregateId, expectedVersion, commandType);
            }
            if (fenceLost(e) || (keyed && schemaLost(e, "fenced_append"))) {
                return appendAggregateEvent(eventMessage, aggregateId, idempotencyKey, commandType, expectedVersion);
            }
            throw e;
        }
    }

    private static final String APES_APPEND_SQL =
            "INSERT INTO es__events (event_sequence_number, aggregate_id, event_message, event_name, context) "
                    + "VALUES (?, ?, ?, ?, ?)";

    /**
     * The events of one aggregate command (F9), in one transaction: all of them or none. Under
     * CPES the transaction takes {@code es-lock} first (bounded by the {@link AppendDeadline}) and
     * keeps it to the commit, so the events get consecutive sequence numbers and no other event
     * falls between them. With the fence they take versions {@code expectedVersion + 1},
     * {@code + 2}...: one taken version refuses all of them. The idempotency key goes on the last
     * event, the one the caller is answered with, so a replay answers with it too.
     *
     * <p>Not through the group commit: several events from one command are rare, and a
     * transaction of their own keeps the batch statement single-row.
     */
    @Override
    public long appendAggregateEvents(List<? extends EventMessage<?>> events, String aggregateId,
                                      String idempotencyKey, String commandType, Long expectedVersion) {
        if (events.size() == 1) {
            return appendAggregateEvent(events.getFirst(), aggregateId, idempotencyKey, commandType, expectedVersion);
        }
        boolean fenced = expectedVersion != null && aggregateId != null && fenceSupported();
        boolean keyed = idempotencyKey != null && idempotencySupported();
        var json = new ArrayList<String>(events.size());
        for (var event : events) {
            try {
                json.add(mapper.writeValueAsString(event));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        long deadline = AppendDeadline.current();
        if (AppendDeadline.passed(deadline)) throw AppendDeadline.expired("before its append");
        try {
            Long last = jdbcTemplate.execute((ConnectionCallback<Long>) c -> {
                boolean own = c.getAutoCommit();
                if (own) c.setAutoCommit(false);
                try {
                    if (MODE != EventStoreMode.APES) lockEsLock(c, deadline);
                    long seq = -1;
                    for (int i = 0; i < events.size(); i++) {
                        var event = events.get(i);
                        boolean withKey = keyed && i == events.size() - 1;
                        var args = new ArrayList<Object>();
                        if (MODE == EventStoreMode.APES) {
                            seq = snowflake.nextId();
                            args.add(seq);
                        }
                        args.add(aggregateId);
                        args.add(json.get(i));
                        args.add(event.getEventName());
                        args.add(event.getContext());
                        if (fenced) args.add(expectedVersion + 1 + i);
                        if (withKey) {
                            args.add(idempotencyKey);
                            args.add(commandType);
                        }
                        String sql = MODE == EventStoreMode.APES
                                ? (fenced ? (withKey ? APES_FENCED_KEYED_APPEND_SQL : APES_FENCED_APPEND_SQL)
                                : (withKey ? APES_KEYED_APPEND_SQL : APES_APPEND_SQL))
                                : (fenced ? (withKey ? CPES_FENCED_KEYED_APPEND_SQL : CPES_FENCED_APPEND_SQL)
                                : (withKey ? CPES_KEYED_APPEND_SQL : CPES_APPEND_SQL));
                        try (var ps = c.prepareStatement(sql)) {
                            for (int a = 0; a < args.size(); a++) ps.setObject(a + 1, args.get(a));
                            if (MODE == EventStoreMode.APES) {
                                if (ps.executeUpdate() == 0) throw new IdempotencyKeyConflictException(idempotencyKey);
                            } else {
                                try (var rs = ps.executeQuery()) {
                                    if (!rs.next()) throw new IdempotencyKeyConflictException(idempotencyKey);
                                    seq = rs.getLong(1);
                                }
                            }
                        }
                    }
                    if (own) c.commit();
                    return seq;
                } catch (SQLException | RuntimeException e) {
                    if (own) c.rollback();
                    throw e;
                } finally {
                    if (own) c.setAutoCommit(true);
                }
            });
            return last == null ? -1 : last;
        } catch (DataAccessException e) {
            if (AppendDeadline.lockTimedOut(e)) throw AppendDeadline.expired("while it waited for es-lock");
            if (fenced && UNIQUE_VIOLATION.equals(sqlState(e)) && String.valueOf(e.getMostSpecificCause().getMessage())
                    .contains(AGGREGATE_SEQ_INDEX)) {
                throw fenceConflict(aggregateId, expectedVersion, commandType);
            }
            throw e;
        }
    }

    /** Takes {@code es-lock} for the connection's transaction, waiting no longer than {@code deadline}. */
    private static void lockEsLock(java.sql.Connection c, long deadline) throws SQLException {
        if (deadline != Long.MAX_VALUE) {
            try (var ps = c.prepareStatement("SELECT set_config('lock_timeout', ?, true)")) {
                ps.setString(1, AppendDeadline.lockTimeout(deadline));
                ps.execute();
            }
        }
        try (var st = c.createStatement()) {
            st.execute("SELECT pg_advisory_xact_lock(hashtext('es-lock'))");
        }
    }

    /** The story this event was computed from is stale: drop what this server cached. */
    private AggregateFenceException fenceConflict(String aggregateId, long expectedVersion, String commandType) {
        snapshotCache.remove(aggregateId);
        eventsCache.remove(aggregateId);
        logger.debug("event=aggregate_fence_conflict aggregateId={} expectedVersion={} commandType={} "
                        + "reason=\"another append from the same version committed first; nothing stored\"",
                aggregateId, expectedVersion, commandType);
        return new AggregateFenceException(aggregateId, expectedVersion);
    }

    @Override
    public boolean fencesAggregates() {
        return fenceSupported();
    }

    /**
     * Whether {@code e} says {@code aggregate_seq} is gone; if so, the fence is turned off
     * (re-probed after {@value #IDEMPOTENCY_PROBE_INTERVAL_MS} ms) with a warning.
     */
    private boolean fenceLost(DataAccessException e) {
        if (!"42703".equals(sqlState(e)) || !String.valueOf(e.getMostSpecificCause().getMessage())
                .contains("aggregate_seq")) return false;
        synchronized (this) {
            fenceSupported = false;
            fenceProbeAt = System.currentTimeMillis() + IDEMPOTENCY_PROBE_INTERVAL_MS;
        }
        logger.warn("event=aggregate_fence_unsupported reason=\"es__events.aggregate_seq is gone; aggregate "
                + "events are appended unfenced until it is back\" error=\"{}\"", e.getMostSpecificCause().getMessage());
        return true;
    }

    /**
     * Whether aggregate appends can be fenced (C1): a PostgreSQL {@code es__events} (as
     * {@code search_path} resolves it) with an {@code aggregate_seq} column and a valid unique
     * {@value #AGGREGATE_SEQ_INDEX}. Without them aggregate events are appended unfenced, as
     * before C1, and this logs {@code event=aggregate_fence_unsupported}: a schema managed outside
     * the server that has not applied {@code db/upgrade/es-events-aggregate-seq.sql} yet. A
     * negative answer is re-checked every minute, like {@link #idempotencySupported()}.
     */
    public boolean fenceSupported() {
        if (fenceSupported) return true;
        if (Boolean.FALSE.equals(postgres)) return false;
        long now = System.currentTimeMillis();
        if (now < fenceProbeAt) return false;
        synchronized (this) {
            if (fenceSupported) return true;
            if (now < fenceProbeAt) return false;
            if (!detectPostgres()) {
                logger.warn("event=aggregate_fence_unsupported reason=\"the aggregate fence needs PostgreSQL; "
                        + "aggregate events are appended unfenced\"");
                return false;
            }
            Boolean ready = jdbcTemplate.queryForObject(
                    "SELECT coalesce((SELECT i.indisvalid AND i.indisunique FROM pg_index i " +
                            "JOIN pg_class c ON c.oid = i.indexrelid " +
                            "WHERE i.indrelid = to_regclass('es__events') AND c.relname = ?), false) " +
                            "AND EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = to_regclass('es__events') " +
                            "AND attname = 'aggregate_seq' AND NOT attisdropped)",
                    Boolean.class, AGGREGATE_SEQ_INDEX);
            fenceSupported = Boolean.TRUE.equals(ready);
            if (!fenceSupported) {
                fenceProbeAt = now + IDEMPOTENCY_PROBE_INTERVAL_MS;
                logger.warn("event=aggregate_fence_unsupported index={} reason=\"no aggregate_seq column or valid "
                        + "index; aggregate events are appended unfenced until they exist\" "
                        + "script=classpath:db/upgrade/es-events-aggregate-seq.sql", AGGREGATE_SEQ_INDEX);
            }
            return fenceSupported;
        }
    }

    /**
     * Moves the CPES sequence above the newest stored number when it is behind it, under
     * {@code es-lock}; returns that number, or {@code null} when the sequence was already ahead.
     * It is behind only on a store that APES wrote (Snowflake ids, far above any count of
     * events), which is how such a store becomes CPES: numbering goes on above the last APES id,
     * so every consumer checkpoint stays below every new event.
     */
    static final String CONTINUE_SEQUENCE_SQL =
            "WITH es_lock AS MATERIALIZED (SELECT pg_advisory_xact_lock(hashtext('es-lock'))), "
                    + "top AS MATERIALIZED (SELECT max(event_sequence_number) AS m FROM es__events, es_lock) "
                    + "SELECT setval('event_sequence_number_serial', top.m) FROM top "
                    + "WHERE top.m > (SELECT last_value FROM event_sequence_number_serial)";

    /**
     * The switch from APES to CPES ({@link #CONTINUE_SEQUENCE_SQL}). Every APES server must be
     * stopped first: an APES server still writing takes ids from its clock, millions above the
     * numbers CPES now gives, and a CPES consumer that read one of them skips the CPES events
     * committed below it. An id from the last minute is reported as that hazard. A no-op on a
     * store CPES has always numbered, and on a database other than PostgreSQL.
     */
    private void continueSequenceAboveApesIds() {
        try {
            synchronized (this) {
                if (!detectPostgres()) return;
            }
            var continued = jdbcTemplate.query(CONTINUE_SEQUENCE_SQL, (rs, i) -> rs.getLong(1));
            if (continued.isEmpty()) return;
            long last = continued.getFirst();
            logger.warn("event=es_mode_switched from=APES to=CPES sequence={} reason=\"the newest stored number is "
                    + "above the CPES sequence: numbering goes on above it\"", last);
            if (last >= snowflake.forInstant(Instant.now().minus(1, ChronoUnit.MINUTES))) {
                logger.error("event=es_mode_switch_while_apes_active newest={} reason=\"an APES id from the last "
                        + "minute: if an APES server is still running, stop it and restart every server with "
                        + "evento.es.mode=CPES, or CPES consumers can skip events\"", last);
            }
        } catch (RuntimeException e) {
            logger.warn("event=es_sequence_check_failed error=\"{}\"", e.getMessage());
        }
    }

    /** Whether the database is PostgreSQL; asked once (call holding the monitor). */
    private boolean detectPostgres() {
        if (postgres == null) {
            String product = jdbcTemplate.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
            postgres = product != null && product.toLowerCase(java.util.Locale.ROOT).contains("postgresql");
        }
        return postgres;
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
            if (!detectPostgres()) {
                logger.warn("event=idempotency_key_unsupported reason=\"idempotency keys need "
                        + "PostgreSQL; keyed commands run without exactly-once\"");
                return false;
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
     * per command. The aggregate's version (C1) is one more branch ({@link #STORY_VERSION_ROW}).
     */
    @Override
    public KeyedAggregateStory fetchAggregateStory(String aggregateId, boolean invalidateCaches,
                                                   boolean invalidateSnapshot, String idempotencyKey) {
        boolean fenced = fenceSupported();
        if (idempotencyKey == null || !idempotencySupported()) {
            return fencedStory(aggregateId, invalidateCaches, invalidateSnapshot, null, fenced);
        }
        try {
            return fencedStory(aggregateId, invalidateCaches, invalidateSnapshot, idempotencyKey, fenced);
        } catch (DataAccessException e) {
            if (!schemaLost(e, "story_lookup")) throw e;
            return fencedStory(aggregateId, invalidateCaches, invalidateSnapshot, null, fenced);
        }
    }

    private KeyedAggregateStory fencedStory(String aggregateId, boolean invalidateCaches, boolean invalidateSnapshot,
                                            String idempotencyKey, boolean fenced) {
        if (!fenced) return fetchStory(aggregateId, invalidateCaches, invalidateSnapshot, idempotencyKey, false);
        try {
            return fetchStory(aggregateId, invalidateCaches, invalidateSnapshot, idempotencyKey, true);
        } catch (DataAccessException e) {
            if (!fenceLost(e)) throw e;
            return fetchStory(aggregateId, invalidateCaches, invalidateSnapshot, idempotencyKey, false);
        }
    }

    private KeyedAggregateStory fetchStory(String aggregateId,
                                           boolean invalidateAggregateCaches,
                                           boolean invalidateAggregateSnapshot,
                                           String idempotencyKey) {
        return fetchStory(aggregateId, invalidateAggregateCaches, invalidateAggregateSnapshot, idempotencyKey, false);
    }

    /**
     * @param withVersion also read the aggregate's version ({@link #STORY_VERSION_ROW}). The
     *                    version is only returned when the story holds the event that set it (or
     *                    the snapshot covers it): a story assembled from this server's cache that
     *                    missed an event (an APES id below the cached range) is fetched again
     *                    without the cache, rather than fenced at a version it does not end at.
     */
    private KeyedAggregateStory fetchStory(String aggregateId,
                                           boolean invalidateAggregateCaches,
                                           boolean invalidateAggregateSnapshot,
                                           String idempotencyKey,
                                           boolean withVersion) {
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
        String recordColumns = idempotencyKey == null ? " " : STORY_ROW_RECORD_COLUMNS;
        String sql = "select 0, event_sequence_number, event_message" + recordColumns + "from es__events " + where;
        if (idempotencyKey != null) {
            sql += STORY_RECORD_ROW;
            params = java.util.Arrays.copyOf(params, params.length + 2);
            params[params.length - 2] = idempotencyKey;
            params[params.length - 1] = idempotencyKey;
        }
        if (withVersion) {
            sql += STORY_VERSION_ROW.formatted(recordColumns.stripTrailing());
            params = java.util.Arrays.copyOf(params, params.length + 1);
            params[params.length - 1] = aggregateId;
        }
        sql += idempotencyKey == null && !withVersion ? "order by event_sequence_number" : " ORDER BY 1, 2";

        final long fetchedAfter = max;
        var record = new IdempotencyRecord[1];
        var inserted = new int[1];
        // {sequence number of the newest fenced event, its version}; none yet: {-1, 0}.
        var newest = new long[]{-1, 0};
        jdbcTemplate.query(sql, (java.sql.ResultSet rs) -> {
            if (rs.getInt(1) == 1) {
                record[0] = readRecord(rs, 2);
                return;
            }
            if (rs.getInt(1) == 2) {
                newest[0] = rs.getLong(2);
                newest[1] = Long.parseLong(rs.getString(3));
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
        Long version = null;
        if (withVersion) {
            boolean holdsNewest = newest[0] < 0
                    || (base != null && newest[0] <= base.getEventSequenceNumber())
                    || events.stream().anyMatch(e -> e.getEventSequenceNumber() == newest[0]);
            if (!holdsNewest) {
                if (invalidateAggregateCaches && invalidateAggregateSnapshot) {
                    throw new IllegalStateException("aggregate '" + aggregateId + "': the event at version "
                            + newest[1] + " (seq " + newest[0] + ") is missing from its story read from the database");
                }
                logger.warn("event=aggregate_story_stale aggregateId={} version={} seq={} action=refetch",
                        aggregateId, newest[1], newest[0]);
                return fetchStory(aggregateId, true, true, idempotencyKey, true);
            }
            version = newest[1];
        }
        return new KeyedAggregateStory(new AggregateStory(
                base == null ? new SerializedAggregateState<>(null) : base.getAggregateState(),
                events.stream()
                        .filter(e -> base == null || e.getEventSequenceNumber() > base.getEventSequenceNumber())
                        .map(e -> (DomainEventMessage) e.getEventMessage())
                        .toList()
        ), record[0], version);
    }
}
