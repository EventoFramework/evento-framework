package com.evento.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class PgDistributedLock {

    private static final Logger logger = LogManager.getLogger(PgDistributedLock.class);

    private final DataSource lockDatasource;
    private Connection lockCon;

    private static final ConcurrentHashMap<String, LockWrapper> locks = new ConcurrentHashMap<>();

    public PgDistributedLock(DataSource lockDatasource) {
        this.lockDatasource = lockDatasource;
    }

    private synchronized Connection getLockConnection(){
        try {
            if (lockCon == null || !lockCon.isValid(3)) {
                lockCon = lockDatasource.getConnection();
            }
            return lockCon;
        }catch (RuntimeException re){
            throw re;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }


    private static class LockWrapper {
        private final Semaphore lock = new Semaphore(1);
        private final AtomicInteger numberOfThreadsInQueue = new AtomicInteger(1);

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


    public void acquire(String key) {
        if (key == null) return;

        // Acquire local (JVM-level) lock
        LockWrapper lockWrapper = locks.compute(key, (k, v) -> v == null ? new LockWrapper() : v.addThreadInQueue());
        lockWrapper.lock.acquireUninterruptibly();

        // Skip distributed lock when no DataSource is configured (embedded/test mode)
        if (lockDatasource == null) return;

        // Acquire advisory lock in PostgreSQL. The key is bound as a parameter (never
        // concatenated) so a caller-controlled lockId/aggregateId cannot inject SQL.
        try (var stmt = this.getLockConnection().prepareStatement(
                "SELECT pg_advisory_lock(hashtext(?))")) {
            stmt.setString(1, key);
            try (var resultSet = stmt.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalMonitorStateException("Failed to acquire advisory lock for key: " + key);
                }
                // pg_advisory_lock always returns a row; no need to read column
            }
        } catch (Throwable e) {
            // Roll back local lock on failure
            lockWrapper.lock.release();
            exitQueue(key);
            throw new RuntimeException("Failed to acquire advisory lock for key: " + key, e);
        }
    }

    public boolean tryAcquire(String key) {
        if (key == null) return false;

        LockWrapper lockWrapper = locks.compute(key, (k, v) -> v == null ? new LockWrapper() : v.addThreadInQueue());
        boolean localLockAcquired = lockWrapper.lock.tryAcquire();
        if (!localLockAcquired) return false;

        // Skip distributed lock when no DataSource is configured (embedded/test mode)
        if (lockDatasource == null) return true;

        try (var stmt = this.getLockConnection().prepareStatement(
                "SELECT pg_try_advisory_lock(hashtext(?))")) {
            stmt.setString(1, key);
            var resultSet = stmt.executeQuery();
            resultSet.next();
            boolean success = resultSet.getBoolean(1);
            if (!success) {
                lockWrapper.lock.release();  // Roll back local lock
                exitQueue(key);
                return false;
            }
            return true;
        } catch (Throwable e) {
            lockWrapper.lock.release();  // Roll back local lock
            exitQueue(key);
            throw new RuntimeException("Failed to acquire advisory lock for key: " + key, e);
        }
    }

    public void release(String key) {
        if (key == null) return;

        // A thread between acquire() and release() contributes 1 to the wrapper's queue
        // count, so no concurrent exitQueue can drop the count to 0 and unmap it: a
        // legitimate holder always finds its wrapper here. null therefore really means
        // release-without-acquire, and skipping the advisory unlock below is then correct
        // (this session's PG lock count was never incremented for this caller).
        LockWrapper lockWrapper = locks.get(key);
        if (lockWrapper == null) {
            throw new IllegalMonitorStateException("No lock held for key: " + key);
        }

        // Release the local (JVM) lock, then leave the queue atomically
        lockWrapper.lock.release();
        exitQueue(key);

        // Skip distributed unlock when no DataSource is configured (embedded/test mode)
        if (lockDatasource == null) return;

        // Release advisory lock from PostgreSQL (key bound as a parameter, never concatenated)
        try (var stmt = getLockConnection().prepareStatement(
                "SELECT pg_advisory_unlock(hashtext(?))")) {
            stmt.setString(1, key);
            try (var resultSet = stmt.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalMonitorStateException("Failed to release advisory lock for key: " + key + " — no result returned.");
                }
                boolean success = resultSet.getBoolean(1);
                if (!success) {
                    throw new IllegalMonitorStateException("Advisory unlock failed: lock for key '" + key + "' was not held by this session.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL error while releasing advisory lock for key: " + key, e);
        }
    }


    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

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
     * and invite a retry of work that was applied.
     */
    private void releaseWithoutMasking(String key, Throwable primary) {
        try {
            release(key);
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
