package com.evento.common.utils;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cluster-wide mutual exclusion on a string key: a JVM-local semaphore per key plus a
 * PostgreSQL session advisory lock ({@code pg_try_advisory_lock(hashtext(key))}).
 *
 * <h2>Connection ownership</h2>
 * Every <em>held</em> lock owns its own pooled connection: the connection is borrowed on acquire,
 * and on release the lock is unlocked and the connection returned to the pool. An earlier design
 * pinned one connection per {@code PgDistributedLock} instance and ran every acquire and release
 * on it; a single thread blocked in {@code pg_advisory_lock(X)} (waiting on another server) then
 * queued every other acquire <em>and release</em> of that instance behind it, and two servers
 * could deadlock each other until the socket timeout killed the connection. It also meant one
 * dead session silently dropped every lock of that instance at once.
 *
 * <h2>Give it its own pool</h2>
 * Because a held lock pins a connection for as long as it is held, the DataSource passed here
 * should be <em>dedicated to locks</em>, not the pool the lock holders do their work on. With a
 * shared pool, N concurrent holders pin N connections; once N reaches the pool size every holder
 * blocks in {@code getConnection()} for the work it took the lock for (fetching an aggregate
 * story, appending an event), none of them can finish and release, and all of them fail at the
 * pool's connection timeout. With a dedicated pool, exhausting it only makes further
 * <em>acquirers</em> wait: a {@code getConnection()} that times out counts as a failed attempt
 * and is retried within the acquire timeout, while the holders keep working on the other pool
 * and release. The server builds such a pool ({@code evento.lock.pool.*}).
 *
 * <p>Pass the pool's size as {@code maxConnections} and the lock never asks the pool for a
 * connection it cannot hand out at once: a permit per connection is taken first, without waiting
 * in {@link #tryAcquire} and bounded by the caller's remaining wait in {@link #acquire(String,
 * Duration)}. Without it an exhausted pool made every attempt wait the pool's connection timeout
 * (1 s in the server), whatever the caller's deadline, and turned the "non-blocking"
 * {@code tryAcquire} into a one-second stall on the thread that called it.
 *
 * <h2>Reserved key</h2>
 * The key {@value #EVENT_STORE_LOCK_KEY} is the event store's append lock
 * ({@code pg_advisory_xact_lock(hashtext('es-lock'))}), shared with servers that predate the
 * single-statement append. Any <em>other</em> key whose {@code hashtext} equals it would make its
 * holder wait on its own append from another session, which PostgreSQL cannot detect as a
 * deadlock; such a key is locked under {@code hashtext(key || '#evento-lock')} instead
 * ({@link #KEY_EXPR}).
 *
 * <h2>Bounded, fair wait</h2>
 * {@link #acquire(String)} gives up with {@link LockAcquisitionTimeoutException} after the acquire
 * timeout (default {@value #DEFAULT_ACQUIRE_TIMEOUT_MS} ms, overridable with the
 * {@code evento.lock.acquire.timeout.ms} system property or the constructor). Within the JVM only
 * one thread per key reaches the database (the local semaphore); that thread waits for another
 * server in one of two ways:
 * <ul>
 *   <li>Queued in PostgreSQL ({@code pg_advisory_lock} under a {@code lock_timeout}), on a
 *       connection of its own, when the pool is dedicated and at most half of it is already
 *       queued ({@link #queuePermits}). PostgreSQL grants a released lock to the session queued
 *       first, and refuses a {@code pg_try_advisory_lock} that would jump the queue, so servers
 *       take turns. Polling alone starved the remote server: B2 (two servers, one hot
 *       aggregate) measured 50 cmd/s through the server that held the key and 0-20 through the
 *       other, which never polled in the microseconds between the holder's release and its next
 *       local thread's acquire.</li>
 *   <li>Polling {@code pg_try_advisory_lock} with jittered exponential backoff, borrowing a
 *       connection only for each attempt: on a shared pool, or when half the dedicated one is
 *       already queued, so waiters never take the connections holders and other keys need.</li>
 * </ul>
 * Either way a waiter never delays unrelated keys or releases.
 *
 * <h2>Lost locks</h2>
 * PostgreSQL releases a session lock when the session ends (connection reset, socket timeout,
 * primary failover, {@code pg_terminate_backend}) while the JVM still believes it holds it.
 * {@link #verifyHeld(String)} lets a holder check, right before it commits, that its session
 * still holds the advisory lock ({@code pg_locks}); {@link #release(String)} detects the loss
 * when unlocking. Both log
 * {@code event=lock_lost} and throw {@link LockLostException}.
 *
 * <p>With a {@code null} DataSource (embedded/test mode) only the JVM-local semaphore is used.
 */
public class PgDistributedLock {

    private static final Logger logger = LoggerFactory.getLogger(PgDistributedLock.class);

    /** Default bound on how long {@link #acquire(String)} waits for a contended key. */
    public static final long DEFAULT_ACQUIRE_TIMEOUT_MS = 60_000L;

    private static final long INITIAL_BACKOFF_MS = 5;
    private static final long MAX_BACKOFF_MS = 200;
    private static final int VALIDATION_TIMEOUT_SECONDS = 3;
    /**
     * The longest one wait queued in PostgreSQL lasts before the waiter re-queues, so a waiter
     * whose JVM shuts down is never parked in the database for the whole acquire timeout.
     */
    private static final long MAX_QUEUED_WAIT_MS = 5_000;
    /** PostgreSQL's {@code lock_not_available}: the {@code lock_timeout} of a queued wait ran out. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    /** The event store's append lock key; see "Reserved key" above. */
    public static final String EVENT_STORE_LOCK_KEY = "es-lock";
    static final String COLLISION_SUFFIX = "#evento-lock";

    /**
     * The advisory lock id of a key, as SQL with four {@code ?} placeholders all bound to the key
     * ({@link #bindKey}): {@code hashtext(key)}, except for a key other than
     * {@value #EVENT_STORE_LOCK_KEY} whose hash collides with it.
     */
    static final String KEY_EXPR =
            "(CASE WHEN ? <> '" + EVENT_STORE_LOCK_KEY + "' AND hashtext(?) = hashtext('" + EVENT_STORE_LOCK_KEY + "') " +
            "THEN hashtext(? || '" + COLLISION_SUFFIX + "') ELSE hashtext(?) END)";

    /**
     * Whether the current session holds the {@code bigint} advisory lock {@code hashtext(key)}.
     * pg_locks splits a bigint key into {@code classid} (high 32 bits) and {@code objid} (low 32
     * bits) with {@code objsubid = 1}; hashtext is a signed int4, so a negative hash sign-extends
     * into {@code classid = 0xFFFFFFFF}. Reassembling the halves gives back the signed key for
     * either sign.
     */
    private static final String HOLDS_ADVISORY_LOCK_SQL =
            "SELECT EXISTS (SELECT 1 FROM pg_locks WHERE locktype = 'advisory' AND granted " +
            "AND pid = pg_backend_pid() AND objsubid = 1 " +
            "AND ((classid::bigint << 32) | objid::bigint) = " + KEY_EXPR + "::bigint)";

    /**
     * Waits in PostgreSQL's lock queue for at most the bound {@code lock_timeout}. The materialised
     * CTE sets it before the lock is requested; on a timeout the failed statement's transaction
     * reverts the setting, after a grant {@link #RESET_LOCK_TIMEOUT_SQL} does.
     */
    private static final String QUEUED_LOCK_SQL =
            "WITH t AS MATERIALIZED (SELECT set_config('lock_timeout', ?, false)) " +
            "SELECT pg_advisory_lock(" + KEY_EXPR + ") FROM t";
    private static final String RESET_LOCK_TIMEOUT_SQL = "RESET lock_timeout";

    private final DataSource lockDatasource;
    private final Duration acquireTimeout;
    /** One permit per connection of a dedicated lock pool; {@code null} = unbounded (shared pool). */
    private final Semaphore connectionPermits;
    /**
     * How many waiters may queue in PostgreSQL at once, each on a lock connection: half the
     * dedicated pool; {@code null} on a shared pool, where waiters only poll.
     */
    private final Semaphore queuePermits;

    private static final ConcurrentHashMap<String, LockWrapper> locks = new ConcurrentHashMap<>();

    public PgDistributedLock(DataSource lockDatasource) {
        this(lockDatasource, Duration.ofMillis(
                Long.getLong("evento.lock.acquire.timeout.ms", DEFAULT_ACQUIRE_TIMEOUT_MS)));
    }

    public PgDistributedLock(DataSource lockDatasource, Duration acquireTimeout) {
        this(lockDatasource, acquireTimeout, 0);
    }

    /**
     * @param maxConnections the size of {@code lockDatasource} when it is a pool dedicated to this
     *                       lock (see "Give it its own pool"); {@code 0} when it is shared or
     *                       unbounded, in which case each attempt waits on the pool as it would
     */
    public PgDistributedLock(DataSource lockDatasource, Duration acquireTimeout, int maxConnections) {
        if (acquireTimeout == null || acquireTimeout.isNegative() || acquireTimeout.isZero()) {
            throw new IllegalArgumentException("acquireTimeout must be positive");
        }
        if (maxConnections < 0) {
            throw new IllegalArgumentException("maxConnections must not be negative");
        }
        this.lockDatasource = lockDatasource;
        this.acquireTimeout = acquireTimeout;
        this.connectionPermits = maxConnections > 0 ? new Semaphore(maxConnections) : null;
        this.queuePermits = maxConnections > 1 ? new Semaphore(maxConnections / 2) : null;
    }


    private static class LockWrapper {
        private final Semaphore lock = new Semaphore(1);
        private final AtomicInteger numberOfThreadsInQueue = new AtomicInteger(1);
        /**
         * The pooled connection whose session owns the advisory lock. Only the thread holding
         * {@link #lock} writes or reads it; the semaphore's release/acquire provides the
         * happens-before edge between successive holders, {@code volatile} covers
         * {@link #verifyHeld} called from a different thread than the acquirer.
         */
        private volatile Connection connection;

        private LockWrapper addThreadInQueue() {
            numberOfThreadsInQueue.incrementAndGet();
            return this;
        }

        private int removeThreadFromQueue() {
            return numberOfThreadsInQueue.decrementAndGet();
        }

    }

    /**
     * Decrements the wrapper's queue count and unmaps it when it reaches zero, atomically
     * under the same ConcurrentHashMap bin lock that {@code acquire}'s {@code compute} uses.
     * Doing the decrement and the removal as two separate steps lets a concurrent
     * {@code acquire} join a wrapper that is about to be unmapped; its later {@code release}
     * then finds no mapping and throws despite legitimately holding the lock.
     */
    private static void exitQueue(String key) {
        locks.compute(key, (k, w) -> (w == null || w.removeThreadFromQueue() == 0) ? null : w);
    }


    /**
     * Acquires {@code key}, waiting at most the acquire timeout.
     *
     * @throws LockAcquisitionTimeoutException if the key is still held elsewhere at the deadline
     */
    public void acquire(String key) {
        acquire(key, acquireTimeout);
    }

    /**
     * Acquires {@code key}, waiting at most {@code maxWait} or the acquire timeout,
     * whichever is shorter. A caller with a deadline of its own (a command whose sender
     * stops waiting at some point) passes what is left of it, so it never queues for a
     * lock longer than anyone waits for the result.
     *
     * @throws LockAcquisitionTimeoutException if the key is still held elsewhere when the wait ends
     */
    public void acquire(String key, Duration maxWait) {
        if (key == null) return;
        long waitNanos = Math.min(acquireTimeout.toNanos(), Math.max(0L, maxWait.toNanos()));
        long deadline = System.nanoTime() + waitNanos;
        // A wait bounded by the caller's deadline that runs out is the caller's business (the
        // broker logs it as a dropped request); only the lock's own bound is a lock problem.
        boolean callerBound = waitNanos < acquireTimeout.toNanos();

        // Acquire local (JVM-level) lock, bounded by the same deadline.
        LockWrapper lockWrapper = locks.compute(key, (k, v) -> v == null ? new LockWrapper() : v.addThreadInQueue());
        boolean localAcquired;
        try {
            localAcquired = lockWrapper.lock.tryAcquire(waitNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            exitQueue(key);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for lock: " + key, e);
        }
        if (!localAcquired) {
            exitQueue(key);
            throw timeout(key, TimeUnit.NANOSECONDS.toMillis(waitNanos), callerBound);
        }

        // Skip distributed lock when no DataSource is configured (embedded/test mode)
        if (lockDatasource == null) return;

        try {
            long backoff = INITIAL_BACKOFF_MS;
            while (true) {
                Connection owner = tryAdvisoryLock(key, Math.max(0L, deadline - System.nanoTime()));
                if (owner == null && queuePermits != null && queuePermits.tryAcquire()) {
                    try {
                        owner = queuedAdvisoryLock(key, deadline);
                    } finally {
                        queuePermits.release();
                    }
                }
                if (owner != null) {
                    lockWrapper.connection = owner;
                    return;
                }
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw timeout(key, TimeUnit.NANOSECONDS.toMillis(waitNanos), callerBound);
                }
                long sleepMs = Math.min(ThreadLocalRandom.current().nextLong(backoff / 2, backoff + 1),
                        TimeUnit.NANOSECONDS.toMillis(remainingNanos) + 1);
                Thread.sleep(sleepMs);
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        } catch (Throwable e) {
            // Roll back local lock on failure
            lockWrapper.lock.release();
            exitQueue(key);
            if (e instanceof LockAcquisitionTimeoutException t) throw t;
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for lock: " + key, e);
            }
            throw new RuntimeException("Failed to acquire advisory lock for key: " + key, e);
        }
    }

    /**
     * One attempt that never waits: {@code false} when the key is held here or elsewhere, or when
     * every lock connection is in use (with a dedicated pool, see {@code maxConnections}).
     */
    public boolean tryAcquire(String key) {
        if (key == null) return false;

        LockWrapper lockWrapper = locks.compute(key, (k, v) -> v == null ? new LockWrapper() : v.addThreadInQueue());
        boolean localLockAcquired = lockWrapper.lock.tryAcquire();
        if (!localLockAcquired) {
            exitQueue(key);
            return false;
        }

        // Skip distributed lock when no DataSource is configured (embedded/test mode)
        if (lockDatasource == null) return true;

        try {
            Connection owner = tryAdvisoryLock(key, 0L);
            if (owner == null) {
                lockWrapper.lock.release();  // Roll back local lock
                exitQueue(key);
                return false;
            }
            lockWrapper.connection = owner;
            return true;
        } catch (Throwable e) {
            lockWrapper.lock.release();  // Roll back local lock
            exitQueue(key);
            throw new RuntimeException("Failed to acquire advisory lock for key: " + key, e);
        }
    }

    /**
     * One non-blocking attempt on a freshly borrowed connection. Returns the connection (whose
     * session now owns the lock) on success; on failure the connection goes straight back to
     * the pool so a waiter never occupies one between attempts. A pool that cannot hand out a
     * connection within {@code maxWaitNanos} (every lock connection pinned by a holder), or in
     * time at all ({@link SQLTransientConnectionException}: the database briefly unreachable), is
     * a failed attempt, not an error; the caller retries within its own deadline.
     */
    private Connection tryAdvisoryLock(String key, long maxWaitNanos) throws SQLException, InterruptedException {
        Connection con = borrow(key, maxWaitNanos);
        if (con == null) return null;
        boolean locked = false;
        try {
            // Session advisory locks are independent of transactions, but a connection handed out
            // with autocommit off would sit "idle in transaction" for as long as the lock is held.
            if (!con.getAutoCommit()) con.setAutoCommit(true);
            // The key is bound as a parameter (never concatenated) so a caller-controlled
            // lockId/aggregateId cannot inject SQL.
            try (var stmt = con.prepareStatement("SELECT pg_try_advisory_lock(" + KEY_EXPR + ")")) {
                bindKey(stmt, 1, key);
                try (var resultSet = stmt.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalMonitorStateException("Failed to acquire advisory lock for key: " + key);
                    }
                    locked = resultSet.getBoolean(1);
                }
            }
        } catch (Throwable t) {
            // Anything failing after the lock was granted (closing the statement, say) must not
            // hand a lock-owning session back to the pool, where it would keep the key locked for
            // good: abort the connection instead, which ends the session and drops the lock.
            // The abort alone would leave the pool slot taken for good: Hikari recycles an entry
            // only on close(), which then finds the connection dead and evicts it.
            if (locked) discard(con);
            else giveBack(con);
            throw t;
        }
        if (!locked) {
            giveBack(con);
            return null;
        }
        return con;
    }

    /**
     * Waits for {@code key} in PostgreSQL's lock queue, until {@code deadline} or
     * {@link #MAX_QUEUED_WAIT_MS}, on a freshly borrowed connection. Returns the connection (whose
     * session now owns the lock, its {@code lock_timeout} back to the session default) on a grant;
     * {@code null} when the wait timed out or no connection was free in time, with the connection
     * back in the pool.
     */
    private Connection queuedAdvisoryLock(String key, long deadline) throws SQLException, InterruptedException {
        long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remainingMs <= 0) return null;
        Connection con = borrow(key, TimeUnit.MILLISECONDS.toNanos(remainingMs));
        if (con == null) return null;
        long waitMs = Math.min(TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()), MAX_QUEUED_WAIT_MS);
        if (waitMs <= 0) {
            giveBack(con);
            return null;
        }
        boolean locked = false;
        try {
            if (!con.getAutoCommit()) con.setAutoCommit(true);
            try (var stmt = con.prepareStatement(QUEUED_LOCK_SQL)) {
                stmt.setString(1, waitMs + "ms");
                bindKey(stmt, 2, key);
                stmt.executeQuery().close();
                locked = true;
            } catch (SQLException e) {
                if (!LOCK_NOT_AVAILABLE.equals(e.getSQLState())) throw e;
            }
            if (locked) {
                try (var stmt = con.createStatement()) {
                    stmt.execute(RESET_LOCK_TIMEOUT_SQL);
                }
            }
        } catch (Throwable t) {
            // A session that may own the lock never goes back to the pool (see tryAdvisoryLock);
            // a wait that failed other than by timing out may have been granted first.
            discard(con);
            throw t;
        }
        if (!locked) {
            giveBack(con);
            return null;
        }
        return con;
    }

    /**
     * Borrows a lock connection, waiting at most {@code maxWaitNanos} for a free one when the
     * pool is dedicated ({@link #connectionPermits}); {@code null} when none is free in time.
     */
    private Connection borrow(String key, long maxWaitNanos) throws SQLException, InterruptedException {
        if (connectionPermits != null) {
            boolean permitted = maxWaitNanos <= 0
                    ? connectionPermits.tryAcquire()
                    : connectionPermits.tryAcquire(maxWaitNanos, TimeUnit.NANOSECONDS);
            if (!permitted) {
                logger.debug("event=lock_connection_unavailable key={} reason=\"every lock connection is in use\"", key);
                return null;
            }
        }
        try {
            return lockDatasource.getConnection();
        } catch (SQLTransientConnectionException e) {
            releasePermit();
            logger.debug("event=lock_connection_unavailable key={} error=\"{}\"", key, e.getMessage());
            return null;
        } catch (SQLException | RuntimeException | Error e) {
            releasePermit();
            throw e;
        }
    }

    /** Returns a connection that holds no lock to the pool. */
    private void giveBack(Connection con) {
        closeQuietly(con);
        releasePermit();
    }

    /**
     * Ends the session of a connection that may still hold a lock (dropping the lock with it) and
     * then closes the pool proxy, so the pool evicts it and frees its slot.
     */
    private void discard(Connection con) {
        abortQuietly(con);
        closeQuietly(con);
        releasePermit();
    }

    private void releasePermit() {
        if (connectionPermits != null) connectionPermits.release();
    }

    private static void bindKey(java.sql.PreparedStatement stmt, int first, String key) throws SQLException {
        for (int i = 0; i < 4; i++) stmt.setString(first + i, key);
    }

    /**
     * Checks, on the owning session, that PostgreSQL still lists {@code key}'s advisory lock as
     * granted to it ({@code pg_locks}). That is stronger than a liveness check: a connection
     * transparently re-established by a proxy would pass {@code isValid()} without holding the
     * lock. Call it right before committing work that relies on the lock. Costs one round trip; a
     * no-op in embedded mode. It narrows, but cannot close, the window in which the lock is lost
     * between this check and the commit: only a database-level fence can.
     *
     * @throws LockLostException if the lock is no longer held, or holding it cannot be confirmed
     * @throws IllegalMonitorStateException if the key is not held by this JVM
     */
    public void verifyHeld(String key) {
        if (key == null || lockDatasource == null) return;
        LockWrapper lockWrapper = locks.get(key);
        Connection owner = lockWrapper == null ? null : lockWrapper.connection;
        if (owner == null) {
            throw new IllegalMonitorStateException("No lock held for key: " + key);
        }
        boolean held;
        try (var stmt = owner.prepareStatement(HOLDS_ADVISORY_LOCK_SQL)) {
            stmt.setQueryTimeout(VALIDATION_TIMEOUT_SECONDS);
            bindKey(stmt, 1, key);
            try (var resultSet = stmt.executeQuery()) {
                held = resultSet.next() && resultSet.getBoolean(1);
            }
        } catch (SQLException e) {
            throw lockLost(key, "the session owning the advisory lock is gone or unresponsive", e);
        }
        if (!held) {
            throw lockLost(key, "the advisory lock is no longer held by its session", null);
        }
    }

    /**
     * Releases {@code key}: unlocks it in PostgreSQL, returns the owning connection to the pool
     * and releases the JVM-local lock. The local lock and the connection are always released,
     * even when the unlock fails.
     *
     * @throws LockLostException if PostgreSQL had already dropped the lock (dead session, or
     *                           the lock was no longer held by it)
     */
    public void release(String key) {
        if (key == null) return;

        // A thread between acquire() and release() contributes 1 to the wrapper's queue
        // count, so no concurrent exitQueue can drop the count to 0 and unmap it: a
        // legitimate holder always finds its wrapper here. null therefore really means
        // release-without-acquire.
        LockWrapper lockWrapper = locks.get(key);
        if (lockWrapper == null) {
            throw new IllegalMonitorStateException("No lock held for key: " + key);
        }

        // Skip distributed unlock when no DataSource is configured (embedded/test mode)
        if (lockDatasource == null) {
            lockWrapper.lock.release();
            exitQueue(key);
            return;
        }

        Connection owner = lockWrapper.connection;
        if (owner == null) {
            throw new IllegalMonitorStateException("No advisory lock held for key: " + key);
        }
        lockWrapper.connection = null;
        try {
            unlock(key, owner);
        } finally {
            // After an abort (see unlock) the close makes the pool evict the connection.
            giveBack(owner);
            // Unlock in PostgreSQL before letting the next local waiter in, so it does not
            // spend a round trip on a try-lock that is bound to fail.
            lockWrapper.lock.release();
            exitQueue(key);
        }
    }

    private void unlock(String key, Connection owner) {
        try (var stmt = owner.prepareStatement("SELECT pg_advisory_unlock(" + KEY_EXPR + ")")) {
            bindKey(stmt, 1, key);
            try (var resultSet = stmt.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalMonitorStateException("Failed to release advisory lock for key: " + key + " — no result returned.");
                }
                if (!resultSet.getBoolean(1)) {
                    throw lockLost(key, "the advisory lock was no longer held by its session", null);
                }
            }
        } catch (SQLException e) {
            if (!isValidQuietly(owner)) {
                throw lockLost(key, "the session owning the advisory lock is gone", e);
            }
            // The session is alive and may still hold the lock: never hand it back to the pool
            // like that, or the lock would leak to whoever borrows the connection next.
            abortQuietly(owner);
            throw new RuntimeException("SQL error while releasing advisory lock for key: " + key, e);
        }
    }

    private static LockLostException lockLost(String key, String reason, Throwable cause) {
        logger.error("event=lock_lost key={} reason=\"{}\" mutual exclusion is no longer guaranteed", key, reason);
        return new LockLostException(key, "Distributed lock lost for key '" + key + "': " + reason, cause);
    }

    private LockAcquisitionTimeoutException timeout(String key, long waitedMs, boolean callerBound) {
        if (callerBound) {
            logger.debug("event=lock_acquire_timeout key={} timeoutMs={} bound=caller", key, waitedMs);
        } else {
            logger.warn("event=lock_acquire_timeout key={} timeoutMs={}", key, waitedMs);
        }
        return new LockAcquisitionTimeoutException(key,
                "Could not acquire lock '" + key + "' within " + waitedMs + " ms");
    }

    private static boolean isValidQuietly(Connection con) {
        try {
            return con.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    private static void abortQuietly(Connection con) {
        try {
            con.abort(Runnable::run);
        } catch (SQLException | RuntimeException e) {
            logger.debug("event=lock_connection_abort_failed", e);
        }
    }

    private static void closeQuietly(Connection con) {
        try {
            con.close();
        } catch (SQLException | RuntimeException e) {
            logger.debug("event=lock_connection_close_failed", e);
        }
    }


    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

    /**
     * Runs {@code runnable} while holding {@code key}. If the lock turns out to have been lost
     * when it is released, {@link LockLostException} is thrown even though the runnable
     * succeeded: its effects are applied, but they may have overlapped another holder's.
     * Callers that must not commit without the lock call {@link #verifyHeld(String)} inside the
     * runnable, right before committing.
     */
    public void lockedArea(String key, ThrowingRunnable runnable) {
        acquire(key);
        Throwable primary = null;
        try{
            runnable.run();
        }catch (RuntimeException re){
            primary = re;
            throw re;
        } catch (Throwable t) {
            primary = t;
            throw new RuntimeException(t);
        }finally {
            releaseWithoutMasking(key, primary);
        }
    }

    public void tryLockedArea(String key, ThrowingRunnable runnable) {
        if(!tryAcquire(key)){
            return;
        }
        Throwable primary = null;
        try{
            runnable.run();
        }catch (RuntimeException re){
            primary = re;
            throw re;
        } catch (Throwable t) {
            primary = t;
            throw new RuntimeException(t);
        }finally {
            releaseWithoutMasking(key, primary);
        }
    }

    /**
     * Releases the lock without letting a bookkeeping failure replace the critical
     * section's outcome: a release failure is attached as suppressed to an in-flight
     * exception, or logged when the critical section succeeded. The work inside the
     * locked area (e.g. a published event) is already durable at this point, so
     * surfacing a cleanup error to the caller would misreport a success as a failure
     * and invite a retry of work that was applied. The one exception is a
     * {@link LockLostException}: that is not bookkeeping but a correctness signal (the
     * section ran without guaranteed exclusion), so it propagates.
     */
    private void releaseWithoutMasking(String key, Throwable primary) {
        try {
            release(key);
        } catch (LockLostException lost) {
            if (primary != null) {
                primary.addSuppressed(lost);
            } else {
                throw lost;
            }
        } catch (RuntimeException releaseFailure) {
            if (primary != null) {
                primary.addSuppressed(releaseFailure);
            } else {
                logger.error("event=lock_release_failed key={} critical section succeeded; " +
                        "its result is preserved and the advisory lock may be leaked", key, releaseFailure);
            }
        }
    }
}
