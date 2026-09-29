package com.evento.transport;

/**
 * The broker gave up on a request because the caller's deadline had passed, and
 * executed nothing on its behalf: depending on where the deadline passed, the request
 * was never forwarded, never got its lock, or its handler's answer came too late and
 * the broker appended no event for it.
 *
 * <p>Unlike a {@link RequestTimeoutException} this outcome is <strong>determinate
 * </strong> as far as the event store is concerned: no event was stored. (A service
 * handler that was reached may have had side effects of its own outside the broker.)
 * A caller whose own timer has not fired yet (the broker measures the deadline from
 * when the request reached it, so it never fires early) may retry it as a new request.
 *
 * <p>A request that passed the broker's last check before its append and then
 * outlived its deadline is still indeterminate for the caller; only an
 * idempotency key closes that window.
 */
public class RequestExpiredException extends TransportException {

    public RequestExpiredException(String message) {
        super(message);
    }
}
