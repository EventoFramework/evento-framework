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
import com.evento.transport.codec.PayloadEncoding;
import com.evento.transport.message.ResponseError;
import com.evento.transport.message.StreamCancel;
import com.evento.transport.message.StreamCredit;
import com.evento.transport.message.StreamData;
import com.evento.transport.message.StreamRedirect;
import com.evento.application.client.stream.InboundStream;
import com.evento.application.client.stream.OutboundStream;
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
    private final java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
    /** Replies of at least this many bytes are compressed for callers that accept it; 0 never. */
    private final int compressionThreshold;
    /** Whether inbound replies may be compressed (this bundle asked for it), and are decoded. */
    private final boolean decodeReplies;

    // ---- streamed replies (3.0) ----
    /** Streams this bundle is receiving, by correlation id. */
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, InboundStream> inboundStreams =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Streams this bundle's handlers are sending, by correlation id. */
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, OutboundStream> outboundStreams =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Whether the current session can carry stream frames (the broker accepted the capability). */
    private volatile java.util.function.BooleanSupplier streamsAccepted = () -> false;
    /** Where streams are offered on direct connections; null when this bundle offers none. */
    private volatile com.evento.application.client.stream.DirectStreamServer directServer;

    // ---- deadlines and admission (F1, 3.0) ----
    /** Requests being handled at most at once; beyond it they are refused at once. 0 = no bound. */
    private volatile int maxInboundRequests;
    private final java.util.concurrent.atomic.AtomicLong expiredBeforeHandler = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong refusedBusy = new java.util.concurrent.atomic.AtomicLong();

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
        this(correlationTracker, dedupCache, handlerRegistry, businessExecutor, sender, dedupExemptPayloadTypes,
                0, false);
    }

    /**
     * @param compressionThreshold replies of at least this many bytes are compressed when
     *        the request accepts it ({@link com.evento.transport.codec.PayloadEncoding}); 0 never
     * @param decodeReplies whether this bundle's requests accept compressed replies, which are
     *        then decoded before they complete their request
     */
    public InboundDispatcher(BundleCorrelationTracker correlationTracker,
                              ProcessedRequestCache dedupCache,
                              HandlerRegistry handlerRegistry,
                              Executor businessExecutor,
                              Function<Message, java.util.concurrent.CompletableFuture<Void>> sender,
                              java.util.Set<String> dedupExemptPayloadTypes,
                              int compressionThreshold,
                              boolean decodeReplies) {
        this.compressionThreshold = Math.max(0, compressionThreshold);
        this.decodeReplies = decodeReplies;
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
                var stream = inboundStreams.get(r.correlationId());
                if (stream != null) {
                    // A streamed request answered the plain way (an older handler) or failed.
                    stream.accept(r);
                } else if (!correlationTracker.complete(decoded(r))) {
                    log.debug("event=orphan_response correlationId={}", r.correlationId());
                }
            }
            case StreamData d -> {
                var stream = inboundStreams.get(d.correlationId());
                if (stream != null) stream.accept(d);
                else log.debug("event=stream_frame_orphan correlationId={} seq={}", d.correlationId(), d.seq());
            }
            case StreamRedirect r -> {
                var stream = inboundStreams.get(r.correlationId());
                if (stream != null) stream.accept(r);
            }
            case StreamCredit c -> {
                var stream = outboundStreams.get(c.correlationId());
                if (stream != null) stream.grant(c.credits());
            }
            case StreamCancel c -> {
                var outbound = outboundStreams.get(c.correlationId());
                if (outbound != null) outbound.cancel(c.reason());
                var inbound = inboundStreams.get(c.correlationId());
                if (inbound != null) inbound.fail(new IllegalStateException("stream cancelled: " + c.reason()));
            }
            case Request req -> dispatchRequest(req);
            case Notification n -> dispatchNotification(n);
            case Pong p -> log.trace("event=pong_received correlationId={}", p.correlationId());
            default -> log.warn("event=unexpected_inbound type={}", message.getClass().getSimpleName());
        }
    }

    /**
     * Bounds the requests this bundle handles at once (admission control): one more is refused
     * at once with {@link com.evento.transport.TooManyPendingRequestsException}, not queued behind
     * work that may outlast its caller's deadline. Nothing ran for it, so the caller may send it
     * again. {@code 0} (the default) sets no bound.
     */
    public void setMaxInboundRequests(int max) {
        this.maxInboundRequests = Math.max(0, max);
    }

    /** Requests whose caller's deadline passed while they waited here, answered without running. */
    public long expiredBeforeHandlerCount() {
        return expiredBeforeHandler.get();
    }

    /** Requests refused because {@link #setMaxInboundRequests} were already being handled. */
    public long refusedBusyCount() {
        return refusedBusy.get();
    }

    private static long monotonicMillis() {
        return System.nanoTime() / 1_000_000;
    }

    /** Whether {@code response} says the request was not executed: a retry runs it for the first time. */
    static boolean notExecuted(Response response) {
        if (response == null || !response.isError() || response.error() == null) return false;
        String type = response.error().exceptionClassName();
        return com.evento.transport.RequestExpiredException.class.getName().equals(type)
                || com.evento.transport.TooManyPendingRequestsException.class.getName().equals(type);
    }

    private void dispatchRequest(Request req) {
        // The caller's deadline, from when the request reached this bundle (the broker forwards
        // the time the caller had left; measured from the arrival it errs late, never early).
        long deadlineMs = req.deadlineFrom(monotonicMillis());
        int max = maxInboundRequests;
        if (max > 0 && inFlight.get() >= max) {
            refusedBusy.incrementAndGet();
            log.warn("event=request_refused_busy correlationId={} payloadType={} inFlight={} max={}",
                    req.correlationId(), req.payloadType(), inFlight.get(), max);
            trySend(Response.failure(req.correlationId(), ResponseError.of(
                    new com.evento.transport.TooManyPendingRequestsException("refusing " + req.payloadType()
                            + ": this bundle is already handling " + max + " requests; not executed"))));
            return;
        }
        if (req.isStreamed() && streamsAccepted.getAsBoolean()
                && handlerRegistry.requestHandlerFor(req.payloadType())
                        instanceof HandlerRegistry.StreamingRequestHandler streaming) {
            var direct = directServer;
            if (req.streamDirect() && direct != null) {
                // The caller can take it directly: hand it a ticket to this bundle's
                // endpoint; the data then never crosses the broker.
                trySend(direct.offer(req, streaming));
                return;
            }
            runCounted(() -> runStream(req, streaming, sender));
            return;
        }
        if (dedupExemptPayloadTypes.contains(req.payloadType())) {
            // Idempotent request (query): run it directly, never touch the dedup
            // cache — so its response is not retained in memory after sending.
            runCounted(() -> trySend(computeResponse(req, deadlineMs)));
            return;
        }
        var outcome = dedupCache.resolveOrClaim(req.correlationId());
        switch (outcome) {
            case ProcessedRequestCache.Outcome.Replay replay -> {
                log.info("event=request_replayed correlationId={} payloadType={}",
                        req.correlationId(), req.payloadType());
                inFlight.incrementAndGet();
                whenWritten(trySend(replay.response()));
            }
            case ProcessedRequestCache.Outcome.InFlight ignored ->
                    log.info("event=request_inflight_duplicate_dropped correlationId={}",
                            req.correlationId());
            case ProcessedRequestCache.Outcome.Claimed ignored -> runCounted(() -> {
                var response = computeResponse(req, deadlineMs);
                // A request that did not run is not answered from the cache: a retry runs it.
                if (notExecuted(response)) dedupCache.forget(req.correlationId());
                else dedupCache.recordResponse(req.correlationId(), response);
                return trySend(response);
            });
        }
    }

    /**
     * Inbound requests accepted but not yet answered: from dispatch until the
     * reply has been written to the socket (not merely handed to the
     * transport). The fail-back switch waits for this to reach zero before it
     * closes the session, because a reply sent after the close would reach the
     * other broker, which has no route back to the caller — and closing the
     * channel while a large, chunked reply is still being written truncates it.
     */
    public int inFlightRequests() {
        return inFlight.get();
    }

    /** Runs {@code task} counted in flight until the write it returns has completed. */
    private void runCounted(java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> task) {
        inFlight.incrementAndGet();
        try {
            businessExecutor.execute(() -> {
                java.util.concurrent.CompletableFuture<Void> written = null;
                try {
                    written = task.get();
                } finally {
                    whenWritten(written);
                }
            });
        } catch (RuntimeException rejected) {
            inFlight.decrementAndGet();
            throw rejected;
        }
    }

    /** Ends one in-flight count when {@code written} completes, or now if nothing was written. */
    private void whenWritten(java.util.concurrent.CompletableFuture<Void> written) {
        if (written == null) {
            inFlight.decrementAndGet();
        } else {
            written.whenComplete((v, t) -> inFlight.decrementAndGet());
        }
    }

    private Response computeResponse(Request req, long deadlineMs) {
        long now = monotonicMillis();
        if (now >= deadlineMs) {
            // Its caller stopped waiting while it queued here: running it now would do the work
            // for nobody, and a retry may be on its way.
            expiredBeforeHandler.incrementAndGet();
            log.info("event=request_expired_before_handler correlationId={} payloadType={} lateMs={}",
                    req.correlationId(), req.payloadType(), now - deadlineMs);
            return Response.failure(req.correlationId(), ResponseError.of(new com.evento.transport.RequestExpiredException(
                    req.payloadType() + " expired before its handler ran on this bundle; not executed")));
        }
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
            if (req.accepts(PayloadEncoding.DEFLATE)) {
                body = PayloadEncoding.deflateIfWorthwhile(body, compressionThreshold);
            }
            return Response.success(req.correlationId(), req.payloadType(), body);
        } catch (Throwable t) {
            log.warn("event=handler_threw correlationId={} payloadType={}",
                    req.correlationId(), req.payloadType(), t);
            return Response.failure(req.correlationId(), ResponseError.of(t));
        }
    }

    /**
     * Answers a request this bundle sent to itself, on the business executor, exactly as
     * an inbound one: same handler, same reply bytes. A request the handler has not
     * answered within {@code timeout} completes like a remote one that ran out of time
     * (the handler keeps running, as it would behind a broker).
     */
    public java.util.concurrent.CompletableFuture<Response> answerLocally(Request req, java.time.Duration timeout) {
        var reply = new java.util.concurrent.CompletableFuture<Response>();
        long deadlineMs = timeout == null || timeout.isZero() || timeout.isNegative()
                ? Long.MAX_VALUE : monotonicMillis() + timeout.toMillis();
        try {
            businessExecutor.execute(() -> {
                try {
                    reply.complete(computeResponse(req, deadlineMs));
                } catch (Throwable t) {
                    reply.complete(Response.failure(req.correlationId(), ResponseError.of(t)));
                }
            });
        } catch (RuntimeException rejected) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    Response.failure(req.correlationId(), ResponseError.of(rejected)));
        }
        if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
            long ms = timeout.toMillis();
            reply.completeOnTimeout(Response.failure(req.correlationId(), new ResponseError(
                    com.evento.transport.RequestTimeoutException.class.getName(),
                    "request expired after " + ms + "ms (timeout=" + ms + ", answered locally)", null)),
                    ms, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        return reply;
    }

    /** Offers streams on direct connections to callers that ask for it (null: never). */
    public void setDirectServer(com.evento.application.client.stream.DirectStreamServer server) {
        this.directServer = server;
    }

    /** Whether stream frames may be sent on the current session. */
    public void setStreamsAccepted(java.util.function.BooleanSupplier accepted) {
        this.streamsAccepted = accepted == null ? () -> false : accepted;
    }

    /** Starts receiving a stream: frames for {@code stream}'s correlation id go to it. */
    public void registerInbound(InboundStream stream) {
        inboundStreams.put(stream.correlationId(), stream);
    }

    public void unregisterInbound(java.util.UUID correlationId) {
        inboundStreams.remove(correlationId);
    }

    /** Streams being received now. */
    public int inboundStreams() {
        return inboundStreams.size();
    }

    /** Streams being sent now. */
    public int outboundStreams() {
        return outboundStreams.size();
    }

    /**
     * The session ended: streams cannot move to another one (their frames would be lost or
     * repeated). Received streams fail, sent streams stop (their handlers' sources close).
     */
    public void streamsLost(String reason) {
        var cause = new IllegalStateException("stream interrupted: " + reason);
        for (var stream : java.util.List.copyOf(inboundStreams.values())) stream.fail(cause);
        for (var stream : java.util.List.copyOf(outboundStreams.values())) stream.cancel(reason);
    }

    /**
     * Runs a streaming handler for {@code req}, writing frames through {@code frameSender}:
     * the session's transport, the local pipe of a stream this bundle asked itself, or a
     * direct connection. Returns the write of the terminal frame.
     */
    java.util.concurrent.CompletableFuture<Void> runStream(Request req,
                                                           HandlerRegistry.StreamingRequestHandler handler,
                                                           Function<Message, java.util.concurrent.CompletableFuture<Void>> frameSender) {
        var sink = new OutboundStream(req.correlationId(), frameSender, req.streamWindow(),
                java.time.Duration.ofMillis(req.timeoutMillis() > 0 ? req.timeoutMillis() : 30_000),
                req.accepts(PayloadEncoding.DEFLATE), compressionThreshold);
        outboundStreams.put(req.correlationId(), sink);
        try {
            handler.handleStream(req.payload(), new HandlerRegistry.RequestContext(
                    req.correlationId(), req.payloadType(),
                    req.sourceBundleId(), req.sourceInstanceId(),
                    req.sourceBundleVersion(), req.timestampMs()), sink);
            return sink.complete();
        } catch (HandlerRegistry.StreamCancelledException cancelled) {
            log.debug("event=stream_cancelled correlationId={} payloadType={} reason=\"{}\"",
                    req.correlationId(), req.payloadType(), cancelled.getMessage());
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        } catch (Throwable t) {
            log.warn("event=stream_handler_threw correlationId={} payloadType={}",
                    req.correlationId(), req.payloadType(), t);
            return sink.fail(t);
        } finally {
            outboundStreams.remove(req.correlationId());
        }
    }

    /**
     * Answers, here and as a stream, a request this bundle sent to itself: the frames go
     * straight into {@code stream} (credit and cancel straight back), through the same
     * handler path a remote stream takes.
     */
    public void answerLocally(Request req, InboundStream stream) {
        var handler = handlerRegistry.requestHandlerFor(req.payloadType());
        if (!(handler instanceof HandlerRegistry.StreamingRequestHandler streaming)) {
            answerLocally(req, java.time.Duration.ofMillis(req.timeoutMillis()))
                    .thenAccept(stream::accept);
            return;
        }
        Function<Message, java.util.concurrent.CompletableFuture<Void>> pipe = m -> {
            if (m instanceof StreamData d) stream.accept(d);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        };
        try {
            businessExecutor.execute(() -> runStream(req, streaming, pipe));
        } catch (RuntimeException rejected) {
            stream.fail(rejected);
        }
    }

    /** Credit and cancel of a stream this bundle answers itself, from its local caller. */
    public void controlLocally(Message control) {
        switch (control) {
            case StreamCredit c -> {
                var stream = outboundStreams.get(c.correlationId());
                if (stream != null) stream.grant(c.credits());
            }
            case StreamCancel c -> {
                var stream = outboundStreams.get(c.correlationId());
                if (stream != null) stream.cancel(c.reason());
            }
            default -> { }
        }
    }

    /** Whether a request handler is registered here for {@code payloadType}. */
    public boolean handles(String payloadType) {
        return handlerRegistry.requestHandlerFor(payloadType) != null;
    }

    /** A successful reply with its payload decoded, when this bundle accepts encoded replies. */
    private Response decoded(Response r) {
        if (!decodeReplies || r.isError() || !PayloadEncoding.isEncoded(r.payload())) return r;
        try {
            return new Response(r.correlationId(), r.payloadType(), PayloadEncoding.decode(r.payload()),
                    null, r.timestampMs());
        } catch (RuntimeException corrupt) {
            log.warn("event=reply_decode_failed correlationId={} payloadType={}", r.correlationId(), r.payloadType(), corrupt);
            return Response.failure(r.correlationId(), ResponseError.of(corrupt));
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

    /** @return the write, or null if it could not even be started */
    private java.util.concurrent.CompletableFuture<Void> trySend(Message m) {
        try {
            return sender.apply(m);
        } catch (SendFailedException sfe) {
            log.warn("event=reply_send_failed type={}", m.getClass().getSimpleName(), sfe);
            return null;
        }
    }
}
