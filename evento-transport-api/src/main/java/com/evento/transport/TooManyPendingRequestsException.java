package com.evento.transport;

/**
 * The caller has more requests outstanding than it is allowed to, so this one
 * was refused without being sent.
 *
 * <p>This is backpressure, and it is deliberately immediate. The alternative —
 * accepting every request and letting the surplus wait — looks more forgiving
 * but behaves far worse: past a certain arrival rate each request spends its
 * entire timeout queued behind others and then fails anyway, so the caller pays
 * the full deadline to learn nothing and the server spends its capacity on work
 * whose requester has already given up.
 *
 * <p>Unlike {@link RequestTimeoutException} this failure <em>is</em> definite:
 * nothing was transmitted, so no handler ran and no state changed. It is always
 * safe to retry, ideally after backing off or reducing concurrency.
 */
public class TooManyPendingRequestsException extends TransportException {

    public TooManyPendingRequestsException(String message) {
        super(message);
    }
}
