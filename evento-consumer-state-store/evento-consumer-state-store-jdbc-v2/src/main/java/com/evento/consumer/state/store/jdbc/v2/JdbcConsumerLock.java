package com.evento.consumer.state.store.jdbc.v2;

import com.evento.common.messaging.consumer.v2.ConsumerLock;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Database-backed exclusive zone, replacing v1
 * {@code enterExclusiveZone}/{@code leaveExclusiveZone}.
 *
 * <p>Both Postgres advisory locks and MySQL {@code GET_LOCK} are
 * <b>session-scoped</b> — they're tied to the connection that took them and
 * released either explicitly or when that connection closes. Implementation
 * therefore pins a {@link Connection} for the lifetime of the
 * {@link LockHandle}: {@code tryAcquire} borrows from the pool; {@code close}
 * releases the lock and returns the connection.
 *
 * <p>Sizing: budget one connection per concurrently-running consumer on this
 * instance. With Hikari, that means the pool max must exceed the maximum
 * simultaneous live locks.
 */
public final class JdbcConsumerLock implements ConsumerLock {

    private final DataSource dataSource;
    private final SqlDialect dialect;

    public JdbcConsumerLock(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    @Override
    public Optional<LockHandle> tryAcquire(String consumerId) {
        Connection c = null;
        try {
            c = dataSource.getConnection();
            boolean acquired = switch (dialect) {
                case POSTGRES -> tryAcquirePostgres(c, consumerId);
                case MYSQL -> tryAcquireMysql(c, consumerId);
            };
            if (!acquired) {
                safeClose(c);
                return Optional.empty();
            }
            return Optional.of(new Handle(c, consumerId, dialect));
        } catch (SQLException e) {
            safeClose(c);
            throw new RuntimeException("failed to acquire consumer lock for '" + consumerId + "'", e);
        }
    }

    private static boolean tryAcquirePostgres(Connection c, String consumerId) throws SQLException {
        // pg_try_advisory_lock takes a bigint; hashtext returns an int4 — wrap
        // both into one bigint so the lock space mirrors v1's hashtext-based
        // hashing while still using the non-blocking try variant.
        try (PreparedStatement stmt = c.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))")) {
            stmt.setString(1, consumerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return false;
                return rs.getBoolean(1);
            }
        }
    }

    private static boolean tryAcquireMysql(Connection c, String consumerId) throws SQLException {
        // GET_LOCK with timeout=0 is non-blocking; returns 1 (got it), 0 (busy)
        // or NULL (error). Lock names cap at 64 chars on MySQL — hash longer
        // ids to fit.
        String key = consumerId.length() <= 64 ? consumerId : "evt_" + Integer.toHexString(consumerId.hashCode());
        try (PreparedStatement stmt = c.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return false;
                int v = rs.getInt(1);
                return !rs.wasNull() && v == 1;
            }
        }
    }

    private static void safeClose(Connection c) {
        if (c == null) return;
        try { c.close(); } catch (SQLException ignored) { /* pool will handle */ }
    }

    private static final class Handle implements LockHandle {
        private final Connection conn;
        private final String consumerId;
        private final SqlDialect dialect;
        private boolean released;

        Handle(Connection conn, String consumerId, SqlDialect dialect) {
            this.conn = conn;
            this.consumerId = consumerId;
            this.dialect = dialect;
        }

        @Override public String consumerId() { return consumerId; }

        @Override
        public void close() {
            if (released) return;
            released = true;
            try {
                switch (dialect) {
                    case POSTGRES -> releasePostgres();
                    case MYSQL -> releaseMysql();
                }
            } catch (SQLException ignored) {
                // The connection is going back to the pool — even on error,
                // closing it discards any per-session lock state.
            } finally {
                safeClose(conn);
            }
        }

        private void releasePostgres() throws SQLException {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
                stmt.setString(1, consumerId);
                stmt.executeQuery();
            }
        }

        private void releaseMysql() throws SQLException {
            String key = consumerId.length() <= 64 ? consumerId : "evt_" + Integer.toHexString(consumerId.hashCode());
            try (PreparedStatement stmt = conn.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                stmt.setString(1, key);
                stmt.executeQuery();
            }
        }
    }
}
