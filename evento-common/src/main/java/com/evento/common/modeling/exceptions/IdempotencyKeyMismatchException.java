package com.evento.common.modeling.exceptions;

/**
 * A command carried an idempotency key that the broker has already recorded for a
 * <em>different</em> command (another command type, or the same type for another aggregate).
 *
 * <p>Definite, not indeterminate: the broker executed nothing for this command and did not
 * answer it with the other command's event. It means the key was reused by mistake: a caller
 * that minted one key for two intents, a handler that forwarded the key it received to a nested
 * command (derive {@code key + ":" + step} instead), or a creation whose aggregate id is
 * generated anew on each attempt (mint it from the key, or once before the first attempt).
 * Retrying with the same key fails the same way.
 *
 * <p>Travels as an {@link ExceptionWrapper}; a peer without this class sees a
 * {@code RuntimeException} naming it.
 */
public class IdempotencyKeyMismatchException extends RuntimeException {

    public IdempotencyKeyMismatchException(String message) {
        super(message);
    }

    public IdempotencyKeyMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
