package com.evento.common.utils;

/**
 * Thrown by {@link PgDistributedLock#acquire(String)} when the lock could not be obtained within
 * the configured acquire timeout. Nothing is held when this is thrown.
 */
public class LockAcquisitionTimeoutException extends RuntimeException {

    private final String key;

    public LockAcquisitionTimeoutException(String key, String message) {
        super(message);
        this.key = key;
    }

    /** The lock key that could not be acquired. */
    public String getKey() {
        return key;
    }
}
