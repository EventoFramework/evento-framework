package com.evento.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
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
 * <h2>Bounded wait</h2>
 * Waiting never blocks a connection inside PostgreSQL: {@link #acquire(String)} polls
 * {@code pg_try_advisory_lock} with jittered exponential backoff, borrowing a connection only
 * for the duration of each attempt, and gives up with {@link LockAcquisitionTimeoutException}
 * after the acquire timeout (default {@value #DEFAULT_ACQUIRE_TIMEOUT_MS} ms, overridable with the
 * {@code evento.lock.acquire.timeout.ms} system property or the constructor). A waiter therefore
 * never delays unrelated keys or releases. The pool must be sized for the number of locks held
 * concurrently plus the work those holders do (see the server's
 * {@code spring.datasource.hikari.maximum-pool-size}).
 *
 * <h2>Lost locks</h2>
 * PostgreSQL releases a session lock when the session ends (connection reset, socket timeout,
 * primary failover, {@code pg_terminate_backend}) while the JVM still believes it holds it.
 * {@link #verifyHeld(String)} lets a holder check, right before it commits, that the owning
 * session is still alive; {@link #release(String)} detects the loss when unlocking. Both log
 * {@code event=lock_lost} and throw {@link LockLostException}.
 *
 * <p>With a {@code null} DataSource (embedded/test mode) only the JVM-local semaphore is used.
 */
public class PgDistributedLock {

    private static final Logger logger = LogManager.getLogger(PgDistributedLock.class);

    /** Default bound on how long {@link #acquire(String)} waits for a contended key. */
    public static final long DEFAULT_ACQUIRE_TIMEOUT_MS = 60_000L;

    private static final long INITIAL_BACKOFF_MS = 5;
    private static final long MAX_BACKOFF_MS = 200;
    private static final int VALIDATION_TIMEOUT_SECONDS = 3;

    private final DataSource lockDatasource;
    private final Duration acquireTimeout;

    private static final ConcurrentHashMap<String, LockWrapper> locks = new ConcurrentHashMap<>();

    public PgDistributedLock(DataSource lockDatasource) {
        this(lockDatasource, Duration.ofMillis(
                Long.getLong("evento.lock.acquire.timeout.ms", DEFAULT_ACQUIRE_TIMEOUT_MS)));
    }

    public PgDistributedLock(DataSource lockDatasource, Duration acquireTimeout) {
        if (acquireTimeout == null || acquireTimeout.isNegative() || acquireTimeout.isZero()) {
            throw new IllegalArgumentException("acquireTimeout must be positive");
        }
        this.lockDatasource = lockDatasource;
        this.acquireTimeout = acquireTimeout;
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
        if (key == null) return;
        long deadline = System.nanoTime() + acquireTimeout.toNanos();

        // Acquire local (JVM-level) lock, bounded by the same deadline.
        LockWrapper lockWrapper = locks.compute(key, (k, v) -> v == null ? new LockWrapper() : v.addThreadInQueue());
        boolean localAcquired;
        try {
            localAcquired = lockWrapper.lock.tryAcquire(acquireTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            exitQueue(key);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for lock: " + key, e);
        }
        if (!localAcquired) {
            exitQueue(key);
            throw timeout(key);
        }

        // Skip distributed lock when no DataSource is configured (embedded/test mode)
        if (lockDatasource == null) return;

        try {
            long backoff = INITIAL_BACKOFF_MS;
            while (true) {
                Connection owner = tryAdvisoryLock(key);
                if (owner != null) {
                    lockWrapper.connection = owner;
                    return;
                }
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw timeout(key);
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
            Connection owner = tryAdvisoryLock(key);
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
     * the pool so a waiter never occupies one between attempts.
     */
    private Connection tryAdvisoryLock(String key) throws SQLException {
        Connection con = lockDatasource.getConnection();
        boolean keep = false;
        try {
            // Session advisory locks are independent of transactions, but a connection handed out
            // with autocommit off would sit "idle in transaction" for as long as the lock is held.
            if (!con.getAutoCommit()) con.setAutoCommit(true);
            // The key is bound as a parameter (never concatenated) so a caller-controlled
            // lockId/aggregateId cannot inject SQL.
            try (var stmt = con.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))")) {
                stmt.setString(1, key);
                try (var resultSet = stmt.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalMonitorStateException("Failed to acquire advisory lock for key: " + key);
                    }
                    keep = resultSet.getBoolean(1);
                    return keep ? con : null;
                }
            }
        } finally {
            if (!keep) closeQuietly(con);
        }
    }

    /**
     * Checks that the session owning {@code key}'s advisory lock is still alive, i.e. that the
     * lock is still held in PostgreSQL. Call it right before committing work that relies on the
     * lock. Costs one round trip. A no-op in embedded mode.
     *
     * @throws LockLostException if the owning session is gone
     * @throws IllegalMonitorStateException if the key is not held by this JVM
     */
    public void verifyHeld(String key) {
        if (key == null || lockDatasource == null) return;
        LockWrapper lockWrapper = locks.get(key);
        Connection owner = lockWrapper == null ? null : lockWrapper.connection;
        if (owner == null) {
            throw new IllegalMonitorStateException("No lock held for key: " + key);
        }
        if (!isValidQuietly(owner)) {
            throw lockLost(key, "the session owning the advisory lock is gone", null);
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
            closeQuietly(owner);
            // Unlock in PostgreSQL before letting the next local waiter in, so it does not
            // spend a round trip on a try-lock that is bound to fail.
            lockWrapper.lock.release();
            exitQueue(key);
        }
    }

    private void unlock(String key, Connection owner) {
        try (var stmt = owner.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
            stmt.setString(1, key);
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

    private LockAcquisitionTimeoutException timeout(String key) {
        logger.warn("event=lock_acquire_timeout key={} timeoutMs={}", key, acquireTimeout.toMillis());
        return new LockAcquisitionTimeoutException(key,
                "Could not acquire lock '" + key + "' within " + acquireTimeout.toMillis() + " ms");
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
