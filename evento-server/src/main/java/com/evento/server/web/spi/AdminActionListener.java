package com.evento.server.web.spi;

import java.util.Map;

/**
 * Told about every state-changing action of the web console (retrying, deleting or replaying dead
 * events, removing a bundle), for example to keep an audit trail, as Evento Enterprise does.
 * {@link com.evento.server.web.AdminActions} calls {@link #started} on the request thread, where
 * the security context and the request are still available, and {@link Completion#finished} when
 * the action has ended, after the bundle has answered for the asynchronous ones.
 */
public interface AdminActionListener {

    /**
     * An action is starting.
     *
     * @param action what is done, such as {@code consumer.dead_queue.consume}
     * @param target what it is done to, such as {@code consumerId}
     * @return called once when the action ends
     */
    Completion started(String action, Map<String, Object> target);

    @FunctionalInterface
    interface Completion {
        /** @param error why the action failed, {@code null} if it succeeded */
        void finished(Throwable error);
    }
}
