package com.evento.transport;

/**
 * The broker gave up on a request because the caller's deadline had passed, and
 * executed nothing on its behalf: depending on where the deadline passed, the request
 * was never forwarded, never got its lock, or an aggregate handler's answer came too
 * late and the broker appended no event for it. A service command that reached its
 * handler is never answered with this: the handler may have had side effects, so its
 * event is stored whenever it answers, and a failure after the deadline stays an
 * indeterminate error.
 *
 * <p><strong>A caller normally never sees this exception.</strong> The broker's
 * deadline is the request's arrival plus its timeout, which is never earlier than the
 * caller's own timer (send plus the same timeout), so by the time the broker drops a
 * request the caller's correlation tracker has already failed it with a
 * {@link RequestTimeoutException}. (The bundle's tracker expires requests on a periodic
 * sweep, so a drop can occasionally reach the caller first; that is the only case it is
 * seen, and it means the same as the timeout would have.) The benefit of the drop is on the broker side: a
 * command nobody waits for any more is not executed (and a retried one is not applied
 * twice), which is visible in {@code evento.server.requests.expired{stage}} and
 * {@code event=request_expired_dropped}. Do not branch on this type to decide whether
 * to retry; treat every timeout as indeterminate.
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
