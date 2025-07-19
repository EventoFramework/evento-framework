package com.evento.common.utils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class PgDistributedLock {

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


    public void acquire(String key) {
        if (key == null) return;

        // Acquire local (JVM-level) lock
        LockWrapper lockWrapper = locks.compute(key, (k, v) -> v == null ? new LockWrapper() : v.addThreadInQueue());
        lockWrapper.lock.acquireUninterruptibly();

        // Acquire advisory lock in PostgreSQL
        try (var stmt = this.getLockConnection().prepareStatement(
                "SELECT pg_advisory_lock(hashtext('"+key+"'))")) {
            try (var resultSet = stmt.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalMonitorStateException("Failed to acquire advisory lock for key: " + key);
                }
                // pg_advisory_lock always returns a row; no need to read column
            }
        } catch (Throwable e) {
            // Roll back local lock on failure
            lockWrapper.lock.release();
            if (lockWrapper.removeThreadFromQueue() == 0) {
                locks.remove(key, lockWrapper);
            }
            throw new RuntimeException("Failed to acquire advisory lock for key: " + key, e);
        }
    }

    public boolean tryAcquire(String key) {
        if (key == null) return false;

        LockWrapper lockWrapper = locks.compute(key, (k, v) -> v == null ? new LockWrapper() : v.addThreadInQueue());
        boolean localLockAcquired = lockWrapper.lock.tryAcquire();
        if (!localLockAcquired) return false;

        try (var stmt = this.getLockConnection().prepareStatement(
                "SELECT pg_try_advisory_lock(hashtext('"+key+"'))")) {
            var resultSet = stmt.executeQuery();
            resultSet.next();
            boolean success = resultSet.getBoolean(1);
            if (!success) {
                lockWrapper.lock.release();  // Roll back local lock
                if (lockWrapper.removeThreadFromQueue() == 0) {
                    locks.remove(key, lockWrapper);
                }
                return false;
            }
            return true;
        } catch (Throwable e) {
            lockWrapper.lock.release();  // Roll back local lock
            if (lockWrapper.removeThreadFromQueue() == 0) {
                locks.remove(key, lockWrapper);
            }
            throw new RuntimeException("Failed to acquire advisory lock for key: " + key, e);
        }
    }

    public void release(String key) {
        if (key == null) return;

        LockWrapper lockWrapper = locks.get(key);
        if (lockWrapper == null) {
            throw new IllegalMonitorStateException("No lock held for key: " + key);
        }

        try {
            // Release the local (JVM) lock
            lockWrapper.lock.release();
        } catch (IllegalMonitorStateException e) {
            throw new RuntimeException("Thread does not hold the local lock for key: " + key, e);
        }

        // Clean up if no threads are waiting
        if (lockWrapper.removeThreadFromQueue() == 0) {
            locks.remove(key, lockWrapper);
        }

        // Release advisory lock from PostgreSQL
        try (var stmt = getLockConnection().prepareStatement(
                "SELECT pg_advisory_unlock(hashtext('"+key+"'))")) {
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
        try{
            runnable.run();
        }catch (RuntimeException re){
            throw re;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }finally {
            release(key);
        }
    }

    public void tryLockedArea(String key, ThrowingRunnable runnable) {
        if(!tryAcquire(key)){
            return;
        }
        try{
            runnable.run();
        }catch (RuntimeException re){
            throw re;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }finally {
            release(key);
        }
    }
}
