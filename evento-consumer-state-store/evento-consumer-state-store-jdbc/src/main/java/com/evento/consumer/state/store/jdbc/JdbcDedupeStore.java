package com.evento.consumer.state.store.jdbc;

import com.evento.common.messaging.consumer.DedupeStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * JDBC implementation of {@link DedupeStore}. Schema lives alongside
 * {@link JdbcConsumerStateStore} (table {@code evento_v2_dedupe}, indexed on
 * {@code claimed_at} for {@link #sweepBefore}).
 *
 * <p>{@link #tryClaim} relies on the primary-key uniqueness constraint:
 * dialects diverge on the "insert or report duplicate" idiom, so we let the
 * SQL surface a duplicate-key exception and translate it to {@code false}.
 * That keeps the contract atomic without dialect-specific upsert pragmas.
 */
public final class JdbcDedupeStore implements DedupeStore {

    private final DataSource dataSource;
    private final SqlDialect dialect;

    public JdbcDedupeStore(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    @Override
    public boolean tryClaim(String consumerId, String eventId) {
        // INSERT IGNORE / ON CONFLICT DO NOTHING — affected==1 means we won.
        var sql = switch (dialect) {
            case POSTGRES -> "INSERT INTO evento_v2_dedupe(consumer_id, event_id) "
                    + "VALUES (?, ?) ON CONFLICT DO NOTHING";
            case MYSQL -> "INSERT IGNORE INTO evento_v2_dedupe(consumer_id, event_id) VALUES (?, ?)";
        };
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, consumerId);
            stmt.setString(2, eventId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "dedupe tryClaim failed for (" + consumerId + "," + eventId + ")", e);
        }
    }

    @Override
    public void release(String consumerId, String eventId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "DELETE FROM evento_v2_dedupe WHERE consumer_id = ? AND event_id = ?")) {
            stmt.setString(1, consumerId);
            stmt.setString(2, eventId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "dedupe release failed for (" + consumerId + "," + eventId + ")", e);
        }
    }

    @Override
    public int sweepBefore(Instant threshold) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement stmt = c.prepareStatement(
                     "DELETE FROM evento_v2_dedupe WHERE claimed_at < ?")) {
            stmt.setTimestamp(1, Timestamp.from(threshold));
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new JdbcConsumerStateStore.ConsumerStateStoreException(
                    "dedupe sweepBefore failed", e);
        }
    }
}
