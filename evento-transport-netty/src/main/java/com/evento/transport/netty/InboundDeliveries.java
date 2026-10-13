package com.evento.transport.netty;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Orders a channel's disconnect after the delivery of the responses it had already
 * received.
 *
 * <p>{@link MessageInboundHandler} hands every inbound frame to the business executor,
 * while the close of the channel is seen on the event loop. A peer that writes a
 * response and closes at once (a bundle leaving its broker at the end of a fail-back
 * drain) used to race the two: the broker announced the disconnect, failed the
 * originator's request with "peer disconnected" and dropped the response that was
 * already on its way to the listener. The disconnect transition now waits for those
 * responses, bounded by {@link #GRACE} so that a stuck listener cannot hold it forever.
 *
 * <p>Only responses are tracked: they are routed without blocking, and they are the
 * frames whose loss turns an executed request into an error. A request still being
 * served when its sender leaves does not delay the disconnect.
 */
final class InboundDeliveries {

    private static final Logger log = LoggerFactory.getLogger(InboundDeliveries.class);

    static final Duration GRACE = Duration.ofSeconds(2);

    private static final AttributeKey<InboundDeliveries> KEY =
            AttributeKey.valueOf("evento.inboundDeliveries");

    private int pending;
    private final List<Runnable> waiting = new ArrayList<>();

    static InboundDeliveries of(Channel channel) {
        var attr = channel.attr(KEY);
        var current = attr.get();
        if (current != null) return current;
        var created = new InboundDeliveries();
        var raced = attr.setIfAbsent(created);
        return raced != null ? raced : created;
    }

    /** A response was read and is about to be handed to the executor (event loop). */
    synchronized void begin() {
        pending++;
    }

    /** That response has been delivered to the listener, successfully or not. */
    void end() {
        List<Runnable> ready;
        synchronized (this) {
            if (--pending > 0 || waiting.isEmpty()) return;
            ready = new ArrayList<>(waiting);
            waiting.clear();
        }
        ready.forEach(InboundDeliveries::runLogged);
    }

    /**
     * Runs {@code action} now when no response is pending; otherwise once every
     * response read so far has been delivered, or after {@link #GRACE}, whichever
     * comes first. The action runs once.
     */
    void runWhenIdle(Channel channel, Runnable action) {
        boolean deferred;
        synchronized (this) {
            deferred = pending > 0;
            if (deferred) waiting.add(action);
        }
        if (!deferred) {
            runLogged(action);
            return;
        }
        try {
            channel.eventLoop().schedule(() -> runIfStillWaiting(action),
                    GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Event loop shutting down: no timer to wait on.
            runIfStillWaiting(action);
        }
    }

    private void runIfStillWaiting(Runnable action) {
        boolean stillWaiting;
        synchronized (this) {
            stillWaiting = waiting.remove(action);
        }
        if (stillWaiting) {
            log.warn("event=disconnect_after_delivery_grace grace_ms={}", GRACE.toMillis());
            runLogged(action);
        }
    }

    private static void runLogged(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            log.error("event=deferred_disconnect_failed", t);
        }
    }
}
