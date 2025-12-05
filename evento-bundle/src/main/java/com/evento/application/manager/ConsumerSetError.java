package com.evento.application.manager;

/**
 * Functional contract used by message handling code to record the latest error
 * occurred while processing a message for a given consumer.
 * <p>
 * Implementations typically persist error metadata (first/last occurrence, count,
 * serialized stacktrace, etc.) into the underlying {@code ConsumerStateStore} so that
 * monitoring tools and status APIs can expose a comprehensive consumer status.
 */
public interface ConsumerSetError {

    /**
     * Records the supplied error as the current last error for the consumer.
     *
     * @param throwable the error to record
     * @throws Throwable if the underlying implementation propagates an error while storing it
     */
    void setError(Throwable throwable) throws Throwable;
}
