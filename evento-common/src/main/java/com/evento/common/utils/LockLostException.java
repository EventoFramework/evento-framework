package com.evento.common.utils;

/**
 * Thrown when a distributed lock that this process believed it held turns out to have been
 * dropped by the database: the session that owned the advisory lock died (connection reset,
 * {@code socketTimeout}, primary failover, {@code pg_terminate_backend}), so PostgreSQL released
 * the lock and another node may already be inside the same critical section.
 *
 * <p>A caller that receives this before committing must not commit: mutual exclusion is no
 * longer guaranteed. A caller that receives it after committing learns that its critical section
 * may have overlapped another holder's and should treat the outcome as needing reconciliation.
 */
public class LockLostException extends IllegalStateException {

    private final String key;

    public LockLostException(String key, String message, Throwable cause) {
        super(message, cause);
        this.key = key;
    }

    /** The lock key that was lost. */
    public String getKey() {
        return key;
    }
}
