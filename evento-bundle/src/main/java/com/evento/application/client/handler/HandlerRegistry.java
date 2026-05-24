package com.evento.application.client.handler;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local lookup of inbound handlers keyed by {@code payloadType}. Used by the
 * bundle-side router to dispatch an inbound {@code Request} or
 * {@code Notification} to the right business handler.
 *
 * <p>Handlers are pure functions on byte arrays: the bundle owns its own
 * payload codec on top, so the framework stays agnostic of the user domain
 * model. {@link RequestHandler} returns the bytes for the {@code Response};
 * {@link NotificationHandler} returns nothing.
 */
public final class HandlerRegistry {

    @FunctionalInterface
    public interface RequestHandler {
        byte[] handle(byte[] payload, RequestContext context) throws Exception;
    }

    @FunctionalInterface
    public interface NotificationHandler {
        void handle(byte[] payload, NotificationContext context) throws Exception;
    }

    public record RequestContext(java.util.UUID correlationId,
                                  String payloadType,
                                  String sourceBundleId,
                                  String sourceInstanceId,
                                  String sourceBundleVersion,
                                  long timestampMs) {}

    public record NotificationContext(java.util.UUID correlationId,
                                       String payloadType,
                                       long timestampMs) {}

    private final Map<String, RequestHandler> requestHandlers = new ConcurrentHashMap<>();
    private final Map<String, NotificationHandler> notificationHandlers = new ConcurrentHashMap<>();

    public void registerRequestHandler(String payloadType, RequestHandler handler) {
        Objects.requireNonNull(payloadType, "payloadType");
        Objects.requireNonNull(handler, "handler");
        if (requestHandlers.putIfAbsent(payloadType, handler) != null) {
            throw new IllegalStateException("request handler already registered for " + payloadType);
        }
    }

    public void registerNotificationHandler(String payloadType, NotificationHandler handler) {
        Objects.requireNonNull(payloadType, "payloadType");
        Objects.requireNonNull(handler, "handler");
        if (notificationHandlers.putIfAbsent(payloadType, handler) != null) {
            throw new IllegalStateException("notification handler already registered for " + payloadType);
        }
    }

    public RequestHandler requestHandlerFor(String payloadType) {
        return requestHandlers.get(payloadType);
    }

    public NotificationHandler notificationHandlerFor(String payloadType) {
        return notificationHandlers.get(payloadType);
    }

    /**
     * The set of payload types the bundle should declare to the server in its
     * {@code BundleRegistrationInfo} — i.e. only request handlers (notifications
     * don't need server-side routing).
     */
    public Set<String> registeredRequestPayloadTypes() {
        return Collections.unmodifiableSet(Set.copyOf(requestHandlers.keySet()));
    }
}
