package com.evento.consumer.state.store.jdbc.v2;

import com.evento.common.messaging.consumer.v2.ConsumerCheckpoint;
import com.evento.common.messaging.consumer.v2.ConsumerErrorState;
import com.evento.common.messaging.consumer.v2.ConsumerStateStore;
import com.evento.common.messaging.consumer.v2.EventCheckpoint;
import com.evento.common.messaging.consumer.v2.OptimisticLockException;
import com.evento.common.messaging.consumer.v2.ProjectorCheckpoint;
import com.evento.common.messaging.consumer.v2.SagaCheckpoint;
import com.evento.common.messaging.consumer.v2.VersionedCheckpoint;

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
 */
public final class JdbcConsumerStateStore implements ConsumerStateStore {

    private static final String KIND_EVENT = "EVENT";
    private static final String KIND_SAGA = "SAGA";
    private static final String KIND_PROJECTOR = "PROJECTOR";

    private final DataSource dataSource;
    private final SqlDialect dialect;

    public JdbcConsumerStateStore(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public DataSource dataSource() { return dataSource; }
    public SqlDialect dialect() { return dialect; }

    // --- Checkpoint ---------------------------------------------------------

    @Override
    public Optional<VersionedCheckpoint> read(String consumerId) {
        var sql = "SELECT kind, last_sequence, version FROM evento_v2_consumer_state WHERE consumer_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, consumerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                var kind = rs.getString(1);
                var seq = rs.getLong(2);
                var version = rs.getLong(3);
                return Optional.of(new VersionedCheckpoint(toCheckpoint(kind, seq), version));
            }
        } catch (SQLException e) {
            throw new ConsumerStateStoreException("read failed for consumer '" + consumerId + "'", e);
        }
    }

    @Override
    public long commit(String consumerId, ConsumerCheckpoint checkpoint, long expectedVersion)
            throws OptimisticLockException {
        var kind = kindOf(checkpoint);
        var seq = checkpoint.lastSequenceNumber();

        try (Connection c = dataSource.getConnection()) {
            long newVersion = expectedVersion == 0L
                    ? insertFirstCommit(c, consumerId, kind, seq)
                    : updateExisting(c, consumerId, kind, seq, expectedVersion);
            // A successful commit clears any previously recorded error.
            clearError(c, consumerId);
            return newVersion;
        } catch (SQLException e) {
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
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "DELETE FROM evento_v2_consumer_state WHERE consumer_id = ?")) {
            stmt.setString(1, consumerId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new ConsumerStateStoreException("delete failed for consumer '" + consumerId + "'", e);
        }
    }

    @Override
    public Stream<String> listConsumers() {
        var ids = new ArrayList<String>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "SELECT consumer_id FROM evento_v2_consumer_state");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) ids.add(rs.getString(1));
        } catch (SQLException e) {
            throw new ConsumerStateStoreException("listConsumers failed", e);
        }
        return ids.stream();
    }

    // --- Enable / disable ---------------------------------------------------

    @Override
    public boolean isEnabled(String consumerId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "SELECT enabled FROM evento_v2_consumer_state WHERE consumer_id = ?")) {
            stmt.setString(1, consumerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return true; // default for unknown consumer
                return rs.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new ConsumerStateStoreException("isEnabled failed for '" + consumerId + "'", e);
        }
    }

    @Override
    public void setEnabled(String consumerId, boolean enabled) {
        // Upsert: keep a row even for consumers that haven't committed yet so
        // the admin toggle survives across restarts. Default kind = EVENT.
        var upsert = switch (dialect) {
            case POSTGRES -> "INSERT INTO evento_v2_consumer_state(consumer_id, kind, last_sequence, version, enabled) "
                    + "VALUES (?, '" + KIND_EVENT + "', 0, 0, ?) "
                    + "ON CONFLICT (consumer_id) DO UPDATE SET enabled = EXCLUDED.enabled";
            case MYSQL -> "INSERT INTO evento_v2_consumer_state(consumer_id, kind, last_sequence, version, enabled) "
                    + "VALUES (?, '" + KIND_EVENT + "', 0, 0, ?) "
                    + "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)";
        };
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(upsert)) {
            stmt.setString(1, consumerId);
            stmt.setBoolean(2, enabled);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new ConsumerStateStoreException("setEnabled failed for '" + consumerId + "'", e);
        }
    }

    // --- Error tracking -----------------------------------------------------

    @Override
    public void setLastError(String consumerId, Throwable error) {
        String stack = stackTraceOf(error);
        // First failure after a clean run pins error_start_at to now;
        // subsequent failures keep it pinned and just bump count + last_error_at.
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
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, consumerId);
            stmt.setString(2, stack);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new ConsumerStateStoreException("setLastError failed for '" + consumerId + "'", e);
        }
    }

    @Override
    public ConsumerErrorState getErrorState(String consumerId) {
        var sql = "SELECT in_error, error_start_at, last_error_at, error_count, error "
                + "FROM evento_v2_consumer_state WHERE consumer_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(sql)) {
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
        } catch (SQLException e) {
            throw new ConsumerStateStoreException("getErrorState failed for '" + consumerId + "'", e);
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
