package com.evento.consumer.state.store.jdbc;

import com.evento.common.messaging.consumer.ConsumerCheckpoint;
import com.evento.common.messaging.consumer.ConsumerErrorState;
import com.evento.common.messaging.consumer.ConsumerStateStore;
import com.evento.common.messaging.consumer.EventCheckpoint;
import com.evento.common.messaging.consumer.OptimisticLockException;
import com.evento.common.messaging.consumer.ProjectorCheckpoint;
import com.evento.common.messaging.consumer.SagaCheckpoint;
import com.evento.common.messaging.consumer.VersionedCheckpoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * JDBC {@link ConsumerStateStore} backed by the {@code evento_v2_consumer_state}
 * table. Holds the checkpoint (with optimistic versioning), the enabled flag
 * and the running error history. Works on Postgres and MySQL via
 * {@link SqlDialect}.
 *
 * <p>Schema lives in {@code db/migration/{postgres,mysql}/v2}. Apply it via
 * {@link FlywayMigrator#migrate} or by including the location in the app's own
 * Flyway configuration.
 *
 * <p>A single {@link Connection} is shared and reused across calls.
 * {@link #getConn()} validates liveness ({@link Connection#isValid(int)}) and
 * reconnects transparently if the connection is stale — this check is the only
 * part that is synchronized. Queries run on the captured connection reference
 * outside the lock; on {@link SQLException} the connection is invalidated via
 * {@link #invalidateConn()} so the next call reconnects.
 *
 * <p>{@link #isEnabled(String)} deliberately does <em>not</em> throw when the
 * database is unreachable: it returns {@code true} (optimistic — keep consuming)
 * and logs at WARN. This prevents a transient network hiccup from flooding the
 * log with one ERROR per consumer per loop iteration.
 */
public final class JdbcConsumerStateStore implements ConsumerStateStore {

    private static final Logger logger = LogManager.getLogger(JdbcConsumerStateStore.class);

    private static final String KIND_EVENT = "EVENT";
    private static final String KIND_SAGA = "SAGA";
    private static final String KIND_PROJECTOR = "PROJECTOR";

    private final DataSource dataSource;
    private final SqlDialect dialect;

    private volatile Connection sharedConn;
    private final Object connLock = new Object();

    public JdbcConsumerStateStore(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public DataSource dataSource() { return dataSource; }
    public SqlDialect dialect() { return dialect; }

    // --- Connection management ----------------------------------------------

    /** Synchronized only for the validity check and reconnect; returns immediately once a live connection exists. */
    private Connection getConn() throws SQLException {
        synchronized (connLock) {
            if (sharedConn == null || sharedConn.isClosed() || !sharedConn.isValid(2)) {
                closeQuietly(sharedConn);
                sharedConn = dataSource.getConnection();
                logger.info("JdbcConsumerStateStore: (re)connected to database");
            }
            return sharedConn;
        }
    }

    /** Marks the connection as bad so the next {@link #getConn()} call reconnects. */
    private void invalidateConn() {
        synchronized (connLock) { sharedConn = null; }
    }

    private static void closeQuietly(Connection c) {
        if (c != null) try { c.close(); } catch (SQLException ignored) {}
    }

    // --- Checkpoint ---------------------------------------------------------

    @Override
    public Optional<VersionedCheckpoint> read(String consumerId) {
        var sql = "SELECT kind, last_sequence, version FROM evento_v2_consumer_state WHERE consumer_id = ?";
        try {
            Connection c = getConn();
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                stmt.setString(1, consumerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    var kind = rs.getString(1);
                    var seq = rs.getLong(2);
                    var version = rs.getLong(3);
                    return Optional.of(new VersionedCheckpoint(toCheckpoint(kind, seq), version));
                }
            }
        } catch (SQLException e) {
            invalidateConn();
            throw new ConsumerStateStoreException("read failed for consumer '" + consumerId + "'", e);
        }
    }

    @Override
    public long commit(String consumerId, ConsumerCheckpoint checkpoint, long expectedVersion)
            throws OptimisticLockException {
        var kind = kindOf(checkpoint);
        var seq = checkpoint.lastSequenceNumber();
        try {
            Connection c = getConn();
            long newVersion = expectedVersion == 0L
                    ? insertFirstCommit(c, consumerId, kind, seq)
                    : updateExisting(c, consumerId, kind, seq, expectedVersion);
            clearError(c, consumerId);
            return newVersion;
        } catch (OptimisticLockException e) {
            throw e;
        } catch (SQLException e) {
            invalidateConn();
            throw new ConsumerStateStoreException("commit failed for consumer '" + consumerId + "'", e);
        }
    }

    private long insertFirstCommit(Connection c, String consumerId, String kind, long seq)
            throws SQLException, OptimisticLockException {
        try (PreparedStatement stmt = c.prepareStatement(dialect.insertIfAbsentSql())) {
            stmt.setString(1, consumerId);
            stmt.setString(2, kind);
            stmt.setLong(3, seq);
            if (stmt.executeUpdate() == 1) return 1L;
        }
        // A row may already exist with no checkpoint committed yet — e.g. one
        // created by setEnabled/setLastError before the first commit. Such a row
        // sits at version 0, so expectedVersion=0 must still succeed: promote it
        // to version 1, guarded by version=0 to keep optimistic-lock semantics.
        try (PreparedStatement stmt = c.prepareStatement(
                "UPDATE evento_v2_consumer_state SET kind = ?, last_sequence = ?, version = 1 "
                        + "WHERE consumer_id = ? AND version = 0")) {
            stmt.setString(1, kind);
            stmt.setLong(2, seq);
            stmt.setString(3, consumerId);
            if (stmt.executeUpdate() == 1) return 1L;
        }
        long actual = readVersionOrZero(c, consumerId);
        throw new OptimisticLockException(consumerId, 0L, actual);
    }

    private long updateExisting(Connection c, String consumerId, String kind, long seq, long expectedVersion)
            throws SQLException, OptimisticLockException {
        var sql = "UPDATE evento_v2_consumer_state "
                + "SET kind = ?, last_sequence = ?, version = version + 1 "
                + "WHERE consumer_id = ? AND version = ?";
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, kind);
            stmt.setLong(2, seq);
            stmt.setString(3, consumerId);
            stmt.setLong(4, expectedVersion);
            if (stmt.executeUpdate() == 1) return expectedVersion + 1;
        }
        long actual = readVersionOrZero(c, consumerId);
        throw new OptimisticLockException(consumerId, expectedVersion, actual);
    }

    private static long readVersionOrZero(Connection c, String consumerId) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT version FROM evento_v2_consumer_state WHERE consumer_id = ?")) {
            stmt.setString(1, consumerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static void clearError(Connection c, String consumerId) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "UPDATE evento_v2_consumer_state SET in_error = false, error_start_at = NULL, "
                        + "last_error_at = NULL, error_count = 0, error = NULL WHERE consumer_id = ?")) {
            stmt.setString(1, consumerId);
            stmt.executeUpdate();
        }
    }

    @Override
    public void delete(String consumerId) {
        try {
            Connection c = getConn();
            try (PreparedStatement stmt = c.prepareStatement(
                    "DELETE FROM evento_v2_consumer_state WHERE consumer_id = ?")) {
                stmt.setString(1, consumerId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            invalidateConn();
            throw new ConsumerStateStoreException("delete failed for consumer '" + consumerId + "'", e);
        }
    }

    @Override
    public Stream<String> listConsumers() {
        var ids = new ArrayList<String>();
        try {
            Connection c = getConn();
            try (PreparedStatement stmt = c.prepareStatement(
                    "SELECT consumer_id FROM evento_v2_consumer_state");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) ids.add(rs.getString(1));
            }
        } catch (SQLException e) {
            invalidateConn();
            throw new ConsumerStateStoreException("listConsumers failed", e);
        }
        return ids.stream();
    }

    // --- Enable / disable ---------------------------------------------------

    /**
     * Returns {@code true} when the DB is unreachable rather than throwing —
     * callers (engine loops) continue consuming optimistically and reconnection
     * is attempted on the next call without flooding the log.
     */
    @Override
    public boolean isEnabled(String consumerId) {
        try {
            Connection c = getConn();
            try (PreparedStatement stmt = c.prepareStatement(
                    "SELECT enabled FROM evento_v2_consumer_state WHERE consumer_id = ?")) {
                stmt.setString(1, consumerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return true;
                    return rs.getBoolean(1);
                }
            }
        } catch (SQLException e) {
            invalidateConn();
            logger.warn("DB unreachable checking enabled state for '{}', assuming enabled: {}",
                    consumerId, e.getMessage());
            return true;
        }
    }

    @Override
    public void setEnabled(String consumerId, boolean enabled) {
        var upsert = switch (dialect) {
            case POSTGRES -> "INSERT INTO evento_v2_consumer_state(consumer_id, kind, last_sequence, version, enabled) "
                    + "VALUES (?, '" + KIND_EVENT + "', 0, 0, ?) "
                    + "ON CONFLICT (consumer_id) DO UPDATE SET enabled = EXCLUDED.enabled";
            case MYSQL -> "INSERT INTO evento_v2_consumer_state(consumer_id, kind, last_sequence, version, enabled) "
                    + "VALUES (?, '" + KIND_EVENT + "', 0, 0, ?) "
                    + "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)";
        };
        try {
            Connection c = getConn();
            try (PreparedStatement stmt = c.prepareStatement(upsert)) {
                stmt.setString(1, consumerId);
                stmt.setBoolean(2, enabled);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            invalidateConn();
            throw new ConsumerStateStoreException("setEnabled failed for '" + consumerId + "'", e);
        }
    }

    // --- Error tracking -----------------------------------------------------

    @Override
    public void setLastError(String consumerId, Throwable error) {
        String stack = stackTraceOf(error);
        var sql = switch (dialect) {
            case POSTGRES -> "INSERT INTO evento_v2_consumer_state"
                    + "(consumer_id, kind, last_sequence, version, in_error, error_start_at, last_error_at, error_count, error) "
                    + "VALUES (?, '" + KIND_EVENT + "', 0, 0, true, now(), now(), 1, ?) "
                    + "ON CONFLICT (consumer_id) DO UPDATE SET "
                    + "in_error = true, last_error_at = now(), error = EXCLUDED.error, "
                    + "error_count = COALESCE(evento_v2_consumer_state.error_count, 0) + 1, "
                    + "error_start_at = CASE WHEN evento_v2_consumer_state.in_error = false THEN now() "
                    + "                       ELSE evento_v2_consumer_state.error_start_at END";
            case MYSQL -> "INSERT INTO evento_v2_consumer_state"
                    + "(consumer_id, kind, last_sequence, version, in_error, error_start_at, last_error_at, error_count, error) "
                    + "VALUES (?, '" + KIND_EVENT + "', 0, 0, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, ?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "in_error = true, last_error_at = CURRENT_TIMESTAMP, error = VALUES(error), "
                    + "error_count = COALESCE(error_count, 0) + 1, "
                    + "error_start_at = IF(in_error = false, CURRENT_TIMESTAMP, error_start_at)";
        };
        try {
            Connection c = getConn();
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                stmt.setString(1, consumerId);
                stmt.setString(2, stack);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            invalidateConn();
            throw new ConsumerStateStoreException("setLastError failed for '" + consumerId + "'", e);
        }
    }

    @Override
    public ConsumerErrorState getErrorState(String consumerId) {
        var sql = "SELECT in_error, error_start_at, last_error_at, error_count, error "
                + "FROM evento_v2_consumer_state WHERE consumer_id = ?";
        try {
            Connection c = getConn();
            try (PreparedStatement stmt = c.prepareStatement(sql)) {
                stmt.setString(1, consumerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return ConsumerErrorState.healthy();
                    boolean inError = rs.getBoolean(1);
                    Instant start = toInstant(rs.getTimestamp(2));
                    Instant last = toInstant(rs.getTimestamp(3));
                    long count = rs.getLong(4);
                    String message = rs.getString(5);
                    if (!inError && start == null && last == null && count == 0L && message == null) {
                        return ConsumerErrorState.healthy();
                    }
                    return new ConsumerErrorState(inError, start, last, count, message);
                }
            }
        } catch (SQLException e) {
            invalidateConn();
            throw new ConsumerStateStoreException("getErrorState failed for '" + consumerId + "'", e);
        }
    }

    // --- Lifecycle ----------------------------------------------------------

    @Override
    public void close() {
        synchronized (connLock) {
            closeQuietly(sharedConn);
            sharedConn = null;
        }
    }

    // --- Internals ----------------------------------------------------------

    private static String kindOf(ConsumerCheckpoint cp) {
        return switch (cp) {
            case EventCheckpoint ignored -> KIND_EVENT;
            case SagaCheckpoint ignored -> KIND_SAGA;
            case ProjectorCheckpoint ignored -> KIND_PROJECTOR;
        };
    }

    private static ConsumerCheckpoint toCheckpoint(String kind, long seq) {
        return switch (kind) {
            case KIND_EVENT -> new EventCheckpoint(seq);
            case KIND_SAGA -> new SagaCheckpoint(seq);
            case KIND_PROJECTOR -> new ProjectorCheckpoint(seq);
            default -> throw new ConsumerStateStoreException("unknown checkpoint kind in db: " + kind);
        };
    }

    private static String stackTraceOf(Throwable t) {
        if (t == null) return null;
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static Instant toInstant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    /** Wrapper for SQL failures that should never escape as checked exceptions. */
    public static final class ConsumerStateStoreException extends RuntimeException {
        public ConsumerStateStoreException(String msg) { super(msg); }
        public ConsumerStateStoreException(String msg, Throwable cause) { super(msg, cause); }
    }

    /** Test/helper accessor for the constants used in the SQL layer. */
    static List<String> knownKinds() { return List.of(KIND_EVENT, KIND_SAGA, KIND_PROJECTOR); }
}
