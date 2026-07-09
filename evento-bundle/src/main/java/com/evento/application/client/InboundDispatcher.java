package com.evento.application.client;

import com.evento.application.client.correlation.BundleCorrelationTracker;
import com.evento.application.client.dedup.ProcessedRequestCache;
import com.evento.application.client.handler.HandlerRegistry;
import com.evento.transport.SendFailedException;
import com.evento.transport.message.Message;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Pong;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import com.evento.transport.message.ResponseError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Per-bundle inbound router. Pattern-matches each inbound message to a
 * destination:
 *
 * <ul>
 *   <li>{@link Response} → complete the matching outbound future via
 *       {@link BundleCorrelationTracker}.</li>
 *   <li>{@link Request} → consult {@link ProcessedRequestCache} (dedup), invoke
 *       the registered handler on the business executor, send the produced
 *       {@link Response} back through the supplied sender. Duplicates short-
 *       circuit to the cached response.</li>
 *   <li>{@link Notification} → invoke the registered notification handler on
 *       the business executor; no reply.</li>
 * </ul>
 *
 * <p>Handlers run on a {@link Executor} provided at construction (virtual
 * threads by default), keeping the network I/O thread free.
 */
public final class InboundDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InboundDispatcher.class);

    private final BundleCorrelationTracker correlationTracker;
    private final ProcessedRequestCache dedupCache;
    private final HandlerRegistry handlerRegistry;
    private final Executor businessExecutor;
    private final Function<Message, java.util.concurrent.CompletableFuture<Void>> sender;
    private final java.util.Set<String> dedupExemptPayloadTypes;

    public InboundDispatcher(BundleCorrelationTracker correlationTracker,
                              ProcessedRequestCache dedupCache,
                              HandlerRegistry handlerRegistry,
                              Executor businessExecutor,
                              Function<Message, java.util.concurrent.CompletableFuture<Void>> sender) {
        this(correlationTracker, dedupCache, handlerRegistry, businessExecutor, sender, java.util.Set.of());
    }

    /**
     * @param dedupExemptPayloadTypes payload types that bypass the exactly-once
     *        {@link ProcessedRequestCache}. Read-only requests (queries) belong
     *        here: they are idempotent, so replay/dedup buys nothing, and keeping
     *        their (potentially large) responses in the cache is pure memory waste.
     */
    public InboundDispatcher(BundleCorrelationTracker correlationTracker,
                              ProcessedRequestCache dedupCache,
                              HandlerRegistry handlerRegistry,
                              Executor businessExecutor,
                              Function<Message, java.util.concurrent.CompletableFuture<Void>> sender,
                              java.util.Set<String> dedupExemptPayloadTypes) {
        this.correlationTracker = correlationTracker;
        this.dedupCache = dedupCache;
        this.handlerRegistry = handlerRegistry;
        this.businessExecutor = businessExecutor;
        this.sender = sender;
        this.dedupExemptPayloadTypes = dedupExemptPayloadTypes == null
                ? java.util.Set.of() : java.util.Set.copyOf(dedupExemptPayloadTypes);
    }

    public Consumer<Message> asMessageSink() {
        return this::dispatch;
    }

    public void dispatch(Message message) {
        switch (message) {
            case Response r -> {
                if (!correlationTracker.complete(r)) {
                    log.debug("event=orphan_response correlationId={}", r.correlationId());
                }
            }
            case Request req -> dispatchRequest(req);
            case Notification n -> dispatchNotification(n);
            case Pong p -> log.trace("event=pong_received correlationId={}", p.correlationId());
            default -> log.warn("event=unexpected_inbound type={}", message.getClass().getSimpleName());
        }
    }

    private void dispatchRequest(Request req) {
        if (dedupExemptPayloadTypes.contains(req.payloadType())) {
            // Idempotent request (query): run it directly, never touch the dedup
            // cache — so its response is not retained in memory after sending.
            businessExecutor.execute(() -> trySend(computeResponse(req)));
            return;
        }
        var outcome = dedupCache.resolveOrClaim(req.correlationId());
        switch (outcome) {
            case ProcessedRequestCache.Outcome.Replay replay -> {
                log.info("event=request_replayed correlationId={} payloadType={}",
                        req.correlationId(), req.payloadType());
                trySend(replay.response());
            }
            case ProcessedRequestCache.Outcome.InFlight ignored ->
                    log.info("event=request_inflight_duplicate_dropped correlationId={}",
                            req.correlationId());
            case ProcessedRequestCache.Outcome.Claimed ignored -> businessExecutor.execute(() -> {
                var response = computeResponse(req);
                dedupCache.recordResponse(req.correlationId(), response);
                trySend(response);
            });
        }
    }

    private Response computeResponse(Request req) {
        var handler = handlerRegistry.requestHandlerFor(req.payloadType());
        if (handler == null) {
            return Response.failure(req.correlationId(),
                    ResponseError.of(new IllegalStateException(
                            "no handler registered for " + req.payloadType())));
        }
        try {
            byte[] body = handler.handle(req.payload(),
                    new HandlerRegistry.RequestContext(
                            req.correlationId(), req.payloadType(),
                            req.sourceBundleId(), req.sourceInstanceId(),
                            req.sourceBundleVersion(), req.timestampMs()));
            return Response.success(req.correlationId(), req.payloadType(), body);
        } catch (Throwable t) {
            log.warn("event=handler_threw correlationId={} payloadType={}",
                    req.correlationId(), req.payloadType(), t);
            return Response.failure(req.correlationId(), ResponseError.of(t));
        }
    }

    private void dispatchNotification(Notification n) {
        var handler = handlerRegistry.notificationHandlerFor(n.payloadType());
        if (handler == null) {
            log.debug("event=unhandled_notification payloadType={}", n.payloadType());
            return;
        }
        businessExecutor.execute(() -> {
            try {
                handler.handle(n.payload(), new HandlerRegistry.NotificationContext(
                        n.correlationId(), n.payloadType(), n.timestampMs()));
            } catch (Throwable t) {
                log.warn("event=notification_handler_threw payloadType={}", n.payloadType(), t);
            }
        });
    }

    private void trySend(Message m) {
        try {
            sender.apply(m);
        } catch (SendFailedException sfe) {
            log.warn("event=reply_send_failed type={}", m.getClass().getSimpleName(), sfe);
        }
    }
}
