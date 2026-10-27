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

    /**
     * A request handler that can also answer with a stream of frames (3.0), when the caller
     * asked for one and the session can carry it; otherwise its plain
     * {@link #handle(byte[], RequestContext)} answers.
     */
    public interface StreamingRequestHandler extends RequestHandler {

        /**
         * Writes the answer to {@code sink}, frame by frame, and returns when it is complete:
         * the framework then ends the stream. Throwing ends it in failure; a cancelled stream
         * surfaces as {@link StreamCancelledException} from {@link StreamSink#send}, which the
         * handler should let propagate after releasing what it holds.
         */
        void handleStream(byte[] payload, RequestContext context, StreamSink sink) throws Exception;
    }

    /** Where a {@link StreamingRequestHandler} writes its frames. */
    public interface StreamSink {

        /**
         * Sends one frame. Blocks while the caller has granted no credit (flow control): the
         * handler produces only as fast as the caller consumes.
         *
         * @throws StreamCancelledException when the caller stopped the stream, went away, or
         *                                  granted no credit within the stream's timeout
         */
        void send(byte[] frame) throws StreamCancelledException, InterruptedException;

        /** Whether the stream was stopped; a handler may check it to stop early. */
        boolean isCancelled();
    }

    /** The caller of a stream stopped it, went away, or stopped granting credit. */
    public static final class StreamCancelledException extends Exception {
        public StreamCancelledException(String message) {
            super(message);
        }
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
