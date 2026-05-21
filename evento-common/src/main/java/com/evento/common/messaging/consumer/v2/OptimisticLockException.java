package com.evento.common.messaging.consumer.v2;

/**
 * Thrown by {@link ConsumerStateStore#commit} when the {@code expectedVersion}
 * passed by the caller does not match the version currently persisted for the
 * given consumer id. The caller must re-read, re-derive the next checkpoint
 * from the fresh state, and retry.
 */
public class OptimisticLockException extends Exception {

    private final String consumerId;
    private final long expectedVersion;
    private final long actualVersion;

    public OptimisticLockException(String consumerId, long expectedVersion, long actualVersion) {
        super("Optimistic lock failure for consumer '" + consumerId
                + "': expected version " + expectedVersion
                + " but found " + actualVersion);
        this.consumerId = consumerId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public String consumerId() {
        return consumerId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long actualVersion() {
        return actualVersion;
    }
}
