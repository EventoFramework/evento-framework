package com.evento.common.messaging.outcome;

/**
 * The idempotency key already has a recorded outcome: another execution of the same command
 * committed first. Thrown by {@link ServiceCommandOutcomeStore#recordInTransaction} (and so by
 * {@link ServiceCommandOutcomes#recordWith}); the transaction it was called in must be rolled
 * back. Let it propagate out of the handler: the bundle answers the command with the outcome
 * recorded first, so both executions get the same answer and only one set of side effects
 * commits.
 */
public class DuplicateServiceOutcomeException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateServiceOutcomeException(String idempotencyKey, Throwable cause) {
        super("an outcome is already recorded for idempotency key '" + idempotencyKey + "'", cause);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /** Whether {@code t} or one of its causes is this exception. */
    public static boolean isCauseOf(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof DuplicateServiceOutcomeException) return true;
            if (c.getCause() == c) break;
        }
        return false;
    }
}
