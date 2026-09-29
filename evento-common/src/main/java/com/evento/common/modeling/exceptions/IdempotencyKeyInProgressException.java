package com.evento.common.modeling.exceptions;

/**
 * Another execution of the same idempotency key was still in flight when this command stopped
 * waiting for it (its deadline passed, or the wait bound did).
 *
 * <p><strong>Indeterminate</strong>, like a timeout: this request executed nothing itself, but
 * the command it duplicates may still commit. It is never reported as "expired, not executed",
 * which would invite a caller to treat the command as not applied. Retry later with the same
 * key: once the other execution ends the retry is answered with its event, or runs the command
 * if that execution failed.
 *
 * <p>Travels as an {@link ExceptionWrapper}; a peer without this class sees a
 * {@code RuntimeException} naming it.
 */
public class IdempotencyKeyInProgressException extends RuntimeException {

    public IdempotencyKeyInProgressException(String message) {
        super(message);
    }

    public IdempotencyKeyInProgressException(String message, Throwable cause) {
        super(message, cause);
    }
}
