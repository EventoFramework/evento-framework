package com.evento.transport;

/**
 * The caller stopped waiting for a response before one arrived.
 *
 * <p>This is <strong>not</strong> a statement that the request failed. The
 * deadline belongs to the caller, not to the handler: when it expires the
 * correlation entry is dropped locally, but nothing cancels the work already in
 * flight. A command that timed out may have been fully applied, may be applying
 * right now, or may never have been routed at all — from the caller's side those
 * three are indistinguishable.
 *
 * <p>That distinction matters because the alternative failures ({@code
 * ExceptionWrapper} carrying a handler exception) <em>are</em> definite: the
 * handler ran and rejected the message. Collapsing both into one generic
 * exception forces callers to treat "we don't know" as "it failed", which turns
 * a slow write into a reported error and makes a naive retry apply the effect
 * twice.
 *
 * <p>Handle it accordingly:
 * <ul>
 *   <li>Report it as indeterminate (HTTP 504, not 500) rather than as a failure.</li>
 *   <li>Retry only idempotent work, or work carrying a deduplication key.</li>
 *   <li>Treat a rising rate of these as a capacity signal — see the server's
 *       {@code evento.server.bus.executor.saturated} meter.</li>
 * </ul>
 */
public class RequestTimeoutException extends TransportException {

    public RequestTimeoutException(String message) {
        super(message);
    }

    public RequestTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
