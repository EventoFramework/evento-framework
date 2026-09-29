package com.evento.transport;

/**
 * The request was sent through one broker and the bundle has since
 * re-attached to a <em>different</em> broker, so its reply can no longer
 * arrive.
 *
 * <p>A broker keeps the replies owed to a disconnected bundle in memory and
 * replays them only when that bundle comes back to <em>the same</em> broker.
 * Once the bundle has failed over to another broker the reply is stranded on
 * the old one, and waiting out the deadline only delays the answer the caller
 * already has: nothing will come. This exception says so at the moment the new
 * broker's {@code Welcome} is seen, instead of after the full request timeout.
 *
 * <p>Like its parent {@link RequestTimeoutException}, the outcome is
 * <strong>indeterminate</strong>: the old broker may have routed the request
 * and the handler may have applied it before the link dropped. Callers that
 * already treat a timeout as "unknown, retry only if idempotent" handle this
 * correctly without change — which is why it extends that type rather than
 * being a sibling.
 */
public class BrokerChangedException extends RequestTimeoutException {

    public BrokerChangedException(String message) {
        super(message);
    }
}
