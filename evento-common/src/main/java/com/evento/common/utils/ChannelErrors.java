package com.evento.common.utils;

/**
 * Cause-chain inspection for transport / channel failures.
 *
 * <p>A "channel error" is anything that signals the bundle could not talk to the
 * server right now — typically a {@code SendFailedException} thrown when the
 * connection supervisor is reconnecting. These errors warrant exponential
 * backoff in the consumer engines so a flapping connection doesn't produce a
 * tight retry storm. All other errors (DB issues, lock contention, etc.) stay
 * on the normal fixed-delay retry.
 *
 * <p>Detection walks the entire cause chain because the original
 * {@code SendFailedException} typically goes through:
 * {@code CompletionException → IllegalStateException(message)} re-wrap in
 * {@code EventoServerAdapter}, then {@code ExecutionException} when the
 * processor calls {@code CompletableFuture.get()}. The class-name match covers
 * the typed path; the message-prefix match covers the re-wrapped path. The
 * prefixes are the literals produced by {@code ConnectionSupervisor.send}.
 */
public final class ChannelErrors {

    private static final String COMMON_SFE = "com.evento.common.messaging.bus.SendFailedException";
    private static final String TRANSPORT_SFE = "com.evento.transport.SendFailedException";

    private ChannelErrors() {}

    public static boolean isChannelError(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            var name = cur.getClass().getName();
            if (COMMON_SFE.equals(name) || TRANSPORT_SFE.equals(name)) {
                return true;
            }
            var msg = cur.getMessage();
            if (msg != null
                    && (msg.startsWith("bundle not ready") || msg.startsWith("transport not bound"))) {
                return true;
            }
            if (cur.getCause() == cur) break;
        }
        return false;
    }
}
