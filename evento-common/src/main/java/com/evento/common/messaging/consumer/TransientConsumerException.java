package com.evento.common.messaging.consumer;

/**
 * Signals that a consumer (saga/projector) failed for a <em>transient</em>
 * reason — a temporarily-unreachable collaborator: broker/transport down, a
 * request timeout, or a refused/reset connection to a downstream dependency.
 *
 * <p>The {@link ConsumerProcessor} throws this instead of dead-lettering the
 * event: the checkpoint is left where it is so the event redelivers once the
 * dependency recovers (at-least-once). Consumer engines treat it as the signal
 * to apply <b>exponential backoff</b> — the same policy as a channel error —
 * so a prolonged outage does not turn into a tight retry storm against the
 * downed dependency. Permanent failures are NOT wrapped in this type; they go
 * to the dead-event queue.
 */
public class TransientConsumerException extends RuntimeException {

    public TransientConsumerException(String message, Throwable cause) {
        super(message, cause);
    }
}
