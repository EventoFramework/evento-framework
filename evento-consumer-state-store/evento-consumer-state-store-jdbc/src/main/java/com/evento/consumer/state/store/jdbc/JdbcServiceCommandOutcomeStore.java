package com.evento.consumer.state.store.jdbc;

import com.evento.common.messaging.outcome.DuplicateServiceOutcomeException;
import com.evento.common.messaging.outcome.ServiceCommandOutcomeStore;
import com.evento.common.messaging.outcome.StoredOutcome;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;

/**
 * JDBC {@link ServiceCommandOutcomeStore} on table {@code evento_v2_service_outcome} (Flyway
 * migration {@code V2__service_command_outcome}, PostgreSQL and MySQL). Put it in the database
 * the service's handlers write to, so {@link #recordInTransaction} can commit the outcome with
 * their writes; every instance of the service must use the same table.
 *
 * <p>The key is the primary key: {@link #record} inserts unless present (first outcome wins),
 * {@link #recordInTransaction} inserts plainly, so a second execution committing the same key
 * fails on the constraint, after waiting for the first one's transaction, and is told
 * {@link DuplicateServiceOutcomeException}.
 */
public final class JdbcServiceCommandOutcomeStore implements ServiceCommandOutcomeStore {

    static final String TABLE = "evento_v2_service_outcome";
    private static final String INSERT = "INSERT INTO " + TABLE
            + " (idempotency_key, command_type, result) VALUES (?, ?, ?)";

    private final DataSource dataSource;
    private final SqlDialect dialect;

    public JdbcServiceCommandOutcomeStore(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    @Override
    public Optional<StoredOutcome> find(String idempotencyKey) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "SELECT command_type, result, recorded_at FROM " + TABLE + " WHERE idempotency_key = ?")) {
            stmt.setString(1, idempotencyKey);
            try (var rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp at = rs.getTimestamp(3);
                return Optional.of(new StoredOutcome(idempotencyKey, rs.getString(1), rs.getBytes(2),
                        at == null ? null : at.toInstant()));
            }
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "service outcome lookup failed for key " + idempotencyKey, e);
        }
    }

    @Override
    public boolean record(String idempotencyKey, String commandType, byte[] serializedResult) {
        var sql = switch (dialect) {
            case POSTGRES -> INSERT + " ON CONFLICT (idempotency_key) DO NOTHING";
            case MYSQL -> "INSERT IGNORE INTO " + TABLE + " (idempotency_key, command_type, result) VALUES (?, ?, ?)";
        };
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(sql)) {
            bind(stmt, idempotencyKey, commandType, serializedResult);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "service outcome record failed for key " + idempotencyKey, e);
        }
    }

    @Override
    public boolean recordInTransaction(Connection connection, String idempotencyKey, String commandType,
                                       byte[] serializedResult) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(INSERT)) {
            bind(stmt, idempotencyKey, commandType, serializedResult);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (isDuplicateKey(e)) throw new DuplicateServiceOutcomeException(idempotencyKey, e);
            throw e;
        }
    }

    @Override
    public int sweepBefore(Instant threshold) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement("DELETE FROM " + TABLE + " WHERE recorded_at < ?")) {
            stmt.setTimestamp(1, Timestamp.from(threshold));
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException("service outcome sweep failed", e);
        }
    }

    private static void bind(PreparedStatement stmt, String key, String commandType, byte[] result) throws SQLException {
        stmt.setString(1, key);
        stmt.setString(2, commandType);
        if (result == null) stmt.setNull(3, Types.BINARY);
        else stmt.setBytes(3, result);
    }

    /** PostgreSQL {@code 23505}; MySQL error 1062 (SQLSTATE {@code 23000}). */
    private static boolean isDuplicateKey(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && ("23505".equals(s.getSQLState()) || s.getErrorCode() == 1062)) {
                return true;
            }
        }
        return false;
    }
}
