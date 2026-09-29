package com.evento.transport;

/**
 * The request never left this process: the bundle was between sessions, or
 * its session was being drained to fail back to the preferred broker, and the
 * send was refused before anything was written. Nothing was executed anywhere,
 * so a retry is always safe, idempotency key or not — unlike a
 * {@link RequestTimeoutException}, whose outcome is unknown.
 *
 * <p>Rebuilt by the bundle from a request refused with {@link SendFailedException}.
 * It is an {@link IllegalStateException} because that is what such a failure
 * surfaced as before the type existed: code catching that keeps working, and
 * code that retries (a consumer's retry accounting, a gateway) can now tell a
 * definite not-sent apart from a handler that failed.
 */
public class RequestNotSentException extends IllegalStateException {

    public RequestNotSentException(String message) {
        super(message);
    }
}
