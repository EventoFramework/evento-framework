package com.evento.consumer.state.store.jdbc;

import com.evento.common.messaging.consumer.ConsumerLock;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
 * <p><b>Affinity (N4, 3.0).</b> A consumer engine takes the lock for every fetch cycle. Released
 * at the end of each cycle, the lock went to whichever replica asked next, so a lane hopped between
 * replicas (lab S16: a global lane written from two regions, fan-out 10 s). The lock is now kept
 * for {@code affinity} after a cycle releases it: the next cycle of the same replica takes it back
 * with no contention (checking that its session is still alive), and the other replicas find it
 * held. A replica that dies loses the lock with its session; one that stops consuming (stopped,
 * failing and backing off longer than the affinity) releases it when the affinity runs out, and
 * another replica takes over. {@link Duration#ZERO} restores the release after every cycle.
 *
 * <p>Sizing: budget one connection per consumer of this instance, held while the instance
 * consumes, plus a few for the state writes. The locks never take more than the pool can spare:
 * with a pool whose maximum size is known ({@code getMaximumPoolSize()}, as on HikariCP, or the
 * {@code maxHeld} given to the constructor) they hold at most {@code max - max(2, max / 4)}
 * connections. A consumer that needs a lock beyond that first ends the affinity of the lock kept
 * idle the longest, or finds the lock busy and tries again on its next cycle: a pool sized for
 * 2.x (one lock per cycle) works as it did, with less affinity, and a warning says so. Without
 * that bound, the consumers that came first kept every connection (they poll more often than the
 * affinity lasts) and the others waited for one forever.
 */
public final class JdbcConsumerLock implements ConsumerLock {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JdbcConsumerLock.class);

    /** How long a lock is kept after a cycle released it, by default. */
    public static final Duration DEFAULT_AFFINITY = Duration.ofSeconds(5);

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final long affinityNanos;
    /** Locks this instance holds: in use by a cycle, or kept for the affinity. */
    private final ConcurrentHashMap<String, Held> held = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reaper;
    /** The most locks (connections) this instance holds at once; {@link Integer#MAX_VALUE}: unbounded. */
    private final int maxHeld;
    /** One permit per connection a lock may hold; null when unbounded. */
    private final java.util.concurrent.Semaphore slots;
    private final java.util.concurrent.atomic.AtomicBoolean warnedBound = new java.util.concurrent.atomic.AtomicBoolean();

    public JdbcConsumerLock(DataSource dataSource, SqlDialect dialect) {
        this(dataSource, dialect, DEFAULT_AFFINITY);
    }

    /** @param affinity how long the lock is kept after a cycle; {@link Duration#ZERO}: released at once */
    public JdbcConsumerLock(DataSource dataSource, SqlDialect dialect, Duration affinity) {
        this(dataSource, dialect, affinity, locksThePoolCanSpare(dataSource));
    }

    /**
     * @param affinity how long the lock is kept after a cycle; {@link Duration#ZERO}: released at once
     * @param maxHeld  the most connections the locks may hold at once (see the class comment);
     *                 0 or less: unbounded
     */
    public JdbcConsumerLock(DataSource dataSource, SqlDialect dialect, Duration affinity, int maxHeld) {
        this.maxHeld = maxHeld <= 0 ? Integer.MAX_VALUE : maxHeld;
        this.slots = maxHeld <= 0 ? null : new java.util.concurrent.Semaphore(maxHeld);
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.affinityNanos = Math.max(0, affinity.toNanos());
        if (affinityNanos > 0) {
            reaper = Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "evento-consumer-lock-affinity");
                t.setDaemon(true);
                return t;
            });
            long period = Math.max(100, affinity.toMillis() / 4);
            reaper.scheduleWithFixedDelay(this::releaseIdle, period, period, TimeUnit.MILLISECONDS);
        } else {
            reaper = null;
        }
    }

    /** A session lock this instance holds. Guarded by its own monitor. */
    private final class Held {
        final Connection conn;
        final String consumerId;
        boolean inUse = true;
        boolean released;
        long idleSince;

        Held(Connection conn, String consumerId) {
            this.conn = conn;
            this.consumerId = consumerId;
        }
    }

    @Override
    public Optional<LockHandle> tryAcquire(String consumerId) {
        var kept = held.get(consumerId);
        if (kept != null) {
            synchronized (kept) {
                if (kept.inUse) return Optional.empty();
                if (!kept.released) {
                    if (alive(kept.conn)) {
                        kept.inUse = true;
                        return Optional.of(new Handle(kept));
                    }
                    // The session died: the lock went with it. Forget it and ask the database.
                    kept.released = true;
                    held.remove(consumerId, kept);
                    safeClose(kept.conn);
                    giveSlot();
                }
            }
        }
        if (!takeSlot()) return Optional.empty();
        Connection c = null;
        try {
            c = dataSource.getConnection();
            boolean acquired = switch (dialect) {
                case POSTGRES -> tryAcquirePostgres(c, consumerId);
                case MYSQL -> tryAcquireMysql(c, consumerId);
            };
            if (!acquired) {
                safeClose(c);
                giveSlot();
                return Optional.empty();
            }
            var fresh = new Held(c, consumerId);
            var previous = held.put(consumerId, fresh);
            if (previous != null) {
                synchronized (previous) {
                    if (!previous.released) {
                        previous.released = true;
                        safeClose(previous.conn);
                        giveSlot();
                    }
                }
            }
            return Optional.of(new Handle(fresh));
        } catch (SQLException | RuntimeException e) {
            safeClose(c);
            giveSlot();
            throw new RuntimeException("failed to acquire consumer lock for '" + consumerId + "'", e);
        }
    }

    /**
     * A permit for one more connection: free, or freed by ending the affinity of the lock kept idle
     * the longest; false when every lock held is in a cycle (the caller finds this one busy).
     */
    private boolean takeSlot() {
        if (slots == null) return true;
        while (!slots.tryAcquire()) {
            Held oldest = null;
            for (var h : held.values()) {
                if (!h.inUse && !h.released && (oldest == null || h.idleSince < oldest.idleSince)) oldest = h;
            }
            warnBound();
            if (oldest == null) return false;
            synchronized (oldest) {
                if (!oldest.inUse && !oldest.released) release(oldest);
            }
        }
        return true;
    }

    private void giveSlot() {
        if (slots != null) slots.release();
    }

    private void warnBound() {
        if (warnedBound.compareAndSet(false, true)) {
            log.warn("event=consumer_lock_pool_bound maxHeld={} - this instance has more consumers than its "
                    + "state-store pool can hold locks for: lock affinity is partly off and consumers take turns. "
                    + "Raise the pool's maximum size above the number of consumers plus a few for the state writes.",
                    maxHeld);
        }
    }

    /** {@code max - max(2, max / 4)} of a pool that tells its maximum size, else unbounded (0). */
    static int locksThePoolCanSpare(DataSource dataSource) {
        try {
            var m = dataSource.getClass().getMethod("getMaximumPoolSize");
            if (m.invoke(dataSource) instanceof Integer max && max > 0) {
                return Math.max(1, max - Math.max(2, max / 4));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A pool that does not tell: unbounded, as before.
        }
        return 0;
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

    private static boolean alive(Connection c) {
        try {
            return c.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /** The end of a cycle: keep the lock for the affinity, or release it. */
    private void endCycle(Held h) {
        synchronized (h) {
            if (h.released) return;
            h.inUse = false;
            h.idleSince = System.nanoTime();
            if (affinityNanos == 0) release(h);
        }
    }

    private void releaseIdle() {
        long now = System.nanoTime();
        for (var h : held.values()) {
            synchronized (h) {
                if (!h.inUse && !h.released && now - h.idleSince >= affinityNanos) release(h);
            }
        }
    }

    /** Unlocks and returns the connection. Call holding {@code h}'s monitor. */
    private void release(Held h) {
        h.released = true;
        held.remove(h.consumerId, h);
        giveSlot();
        try {
            switch (dialect) {
                case POSTGRES -> unlock(h.conn, "SELECT pg_advisory_unlock(hashtext(?))", h.consumerId);
                case MYSQL -> unlock(h.conn, "SELECT RELEASE_LOCK(?)", mysqlKey(h.consumerId));
            }
        } catch (SQLException ignored) {
            // The connection is going back to the pool: even on error, closing it discards
            // any per-session lock state.
        } finally {
            safeClose(h.conn);
        }
    }

    private static void unlock(Connection c, String sql, String key) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.executeQuery();
        }
    }

    private static String mysqlKey(String consumerId) {
        return consumerId.length() <= 64 ? consumerId : "evt_" + Integer.toHexString(consumerId.hashCode());
    }

    /** Releases every lock this instance holds, kept or in use, and stops the affinity timer. */
    @Override
    public void close() {
        if (reaper != null) reaper.shutdownNow();
        for (var h : held.values()) {
            synchronized (h) {
                if (!h.released) release(h);
            }
        }
    }

    private static void safeClose(Connection c) {
        if (c == null) return;
        try { c.close(); } catch (SQLException ignored) { /* pool will handle */ }
    }

    private final class Handle implements LockHandle {
        private final Held held;
        private boolean closed;

        Handle(Held held) {
            this.held = held;
        }

        @Override public String consumerId() { return held.consumerId; }

        /** Ends this cycle's hold; the lock itself is kept for the affinity. Idempotent. */
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            endCycle(held);
        }
    }
}
