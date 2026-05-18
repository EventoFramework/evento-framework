package com.evento.transport;

import com.evento.transport.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Type-keyed dispatcher for inbound messages. Replaces switch-on-class throughout
 * the codebase. Adding a new message type → register a handler at startup; no
 * existing code needs to change (OCP).
 *
 * <p>This class is not thread-safe for concurrent registration: register all handlers
 * during bootstrap, then freeze. Dispatch is thread-safe.
 */
public final class MessageDispatcher<C> {

    private static final Logger log = LoggerFactory.getLogger(MessageDispatcher.class);

    @FunctionalInterface
    public interface Handler<M extends Message, C> {
        void handle(M message, C context);
    }

    private final Map<Class<? extends Message>, Handler<? extends Message, C>> handlers = new HashMap<>();
    private Handler<Message, C> fallback;

    public <M extends Message> MessageDispatcher<C> register(Class<M> type, Handler<M, C> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(type, handler) != null) {
            throw new IllegalStateException("Handler already registered for " + type.getName());
        }
        return this;
    }

    public MessageDispatcher<C> onUnhandled(Handler<Message, C> fallback) {
        this.fallback = fallback;
        return this;
    }

    @SuppressWarnings("unchecked")
    public void dispatch(Message message, C context) {
        if (message == null) return;
        Handler<? extends Message, C> handler = handlers.get(message.getClass());
        if (handler == null) {
            if (fallback != null) {
                fallback.handle(message, context);
            } else {
                log.warn("event=unhandled_message type={} correlationId={}",
                        message.getClass().getSimpleName(), message.correlationId());
            }
            return;
        }
        try {
            ((Handler<Message, C>) handler).handle(message, context);
        } catch (Throwable t) {
            log.error("event=handler_error type={} correlationId={}",
                    message.getClass().getSimpleName(), message.correlationId(), t);
            throw t;
        }
    }
}
