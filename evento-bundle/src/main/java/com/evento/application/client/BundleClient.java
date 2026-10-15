package com.evento.application.client;

import com.evento.application.client.connection.ConnectionSupervisor;
import com.evento.application.client.correlation.BundleCorrelationTracker;
import com.evento.application.client.dedup.ProcessedRequestCache;
import com.evento.application.client.handler.HandlerRegistry;
import com.evento.transport.SendFailedException;
import com.evento.transport.TooManyPendingRequestsException;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.codec.PayloadCodec;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Request;
import com.evento.application.client.stream.InboundStream;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.message.Response;
import com.evento.transport.protocol.ProtocolNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Public façade for the v2 bundle ↔ server connection. Composes the
 * {@link ConnectionSupervisor} (Netty + reconnect + state machine) with the
 * {@link HandlerRegistry} (inbound dispatch table), the
 * {@link BundleCorrelationTracker} (outbound futures) and the
 * {@link ProcessedRequestCache} (exactly-once for inbound requests).
 *
 * <p>The API is deliberately byte-oriented:
 * <pre>{@code
 *   CompletableFuture<Response> reply = bundle.request("com.foo.Cmd", encodedBody, Duration.ofSeconds(5));
 *   bundle.registerRequestHandler("com.bar.Query", (payload, ctx) -> handleAndEncode(payload));
 * }</pre>
 * The bundle's own payload codec (e.g. CBOR/JSON of a domain object) sits one
 * layer above this; framework code never sees the application schema.
 *
 * <p>Lifecycle is explicit. Build with {@link Builder}; call {@link #start()};
 * call {@link #close()} (or {@link #stop(Duration)}) when shutting down. Once
 * started the supervisor keeps the connection up across server restarts with
 * exponential backoff — the public {@link BundleClientState} reflects the
 * higher-level lifecycle, not transient transport hiccups.
 */
public final class BundleClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BundleClient.class);

    private final BundleClientConfig config;
    private final PayloadCodec payloadCodec;
    private final HandlerRegistry handlerRegistry;
    private final BundleCorrelationTracker correlationTracker;
    private final ProcessedRequestCache processedRequestCache;
    private final ConnectionSupervisor supervisor;
    private final InboundDispatcher inboundDispatcher;
    private final com.evento.application.client.metrics.BundleLatencies latencies =
            new com.evento.application.client.metrics.BundleLatencies();
    /** Incremented by every session that passes the handshake. */
    private final java.util.concurrent.atomic.AtomicLong sessionEpoch = new java.util.concurrent.atomic.AtomicLong();
    /** The query payload types this bundle's own handlers answer. */
    private final java.util.Set<String> localQueryTypes;
    /** Whether this bundle is in the broker's available view (enable() called, disable() not since). */
    private volatile boolean enabled;
    private final java.util.concurrent.atomic.LongAdder localQueries = new java.util.concurrent.atomic.LongAdder();
    /** This bundle's endpoint for direct streams, or null when it offers none. */
    private final com.evento.application.client.stream.DirectStreamServer directServer;
    /** Follows the direct streams other bundles offer to this one. */
    private final com.evento.application.client.stream.DirectStreamClient directClient;

    public BundleClient(BundleClientConfig config) {
        this(config, new JacksonCborPayloadCodec());
    }

    public BundleClient(BundleClientConfig config, PayloadCodec payloadCodec) {
        this.config = config;
        this.payloadCodec = payloadCodec;
        this.handlerRegistry = new HandlerRegistry();
        this.correlationTracker = new BundleCorrelationTracker();
        this.processedRequestCache = new ProcessedRequestCache();
        this.supervisor = new ConnectionSupervisor(config, payloadCodec);
        this.localQueryTypes = queryPayloadTypes(config);
        // An auto-enabled bundle is enabled by its registration, without an enable() call.
        this.enabled = config.autoEnable();
        this.inboundDispatcher = new InboundDispatcher(
                correlationTracker,
                processedRequestCache,
                handlerRegistry,
                config.transportConfig().businessExecutor(),
                this::sendInternal,
                queryPayloadTypes(config),
                config.compressionThreshold(),
                config.acceptCompressedReplies()
        );
        this.supervisor.setInboundSink(inboundDispatcher.asMessageSink());
        // Every session that passes the handshake starts a new epoch; idempotent
        // requests remember the epoch they were sent on.
        this.supervisor.onBrokerAccepted(serverInstanceId -> sessionEpoch.incrementAndGet());
        // A reply is buffered by the broker the request went through and is only
        // replayed to this bundle on that broker. Attaching anywhere else strands
        // it; say so now instead of after the whole request timeout.
        this.supervisor.onBrokerAccepted(correlationTracker::failSentOnOtherBroker);
        // No broker keeps the reply of a read for a caller that went away: ask the
        // reads that were in flight again as soon as the new session can send.
        this.supervisor.onStateChange((from, to) -> {
            if (to == BundleClientState.READY) {
                correlationTracker.resendSentBefore(sessionEpoch.get(), supervisor::send);
            }
        });
        // What a fail-back switch waits for before it closes the session (N16).
        this.supervisor.setDrainCounters(inboundDispatcher::inFlightRequests, correlationTracker::pendingCount);
        // Stream frames flow only on a session whose broker accepted the capability, and a
        // stream does not survive its session (its frames would be lost or repeated).
        this.inboundDispatcher.setStreamsAccepted(() -> supervisor.accepted(HandshakeProtocol.CAPABILITY_STREAM));
        this.directServer = config.directStreams() == null ? null
                : new com.evento.application.client.stream.DirectStreamServer(config.directStreams(),
                        config.transportConfig(), config.instanceId(), inboundDispatcher::runStream,
                        inboundDispatcher::dispatch);
        this.inboundDispatcher.setDirectServer(directServer);
        this.directClient = new com.evento.application.client.stream.DirectStreamClient(config.transportConfig(),
                config.bundleId(), config.instanceId(), config.bundleVersion(), config.handshakeTimeout());
        this.supervisor.onStateChange((from, to) -> {
            if (from == BundleClientState.READY && to != BundleClientState.READY) {
                inboundDispatcher.streamsLost("the session to the broker ended (" + to + ")");
            }
        });
    }

    // ---------- Lifecycle ----------

    /**
     * Open the connection and complete the handshake/registration. Returns when
     * the bundle is READY (or when the first attempt fails).
     */
    public CompletableFuture<Void> start() {
        // Bound before the bundle registers: a direct stream may be asked of it at once.
        if (directServer != null) directServer.start();
        return supervisor.start();
    }

    /**
     * Initiate shutdown. Fails every pending outbound request with
     * {@code ShutdownInProgressException} after the deadline; closes the
     * transport; the supervisor thread exits.
     */
    public void stop(Duration deadline) {
        try {
            supervisor.stop(deadline);
        } finally {
            correlationTracker.shutdown(deadline);
            if (directServer != null) directServer.close();
            directClient.close();
        }
    }

    @Override
    public void close() {
        stop(config.shutdownDeadline());
    }

    public BundleClientState state() { return supervisor.state(); }

    /** The {@code serverInstanceId} of the broker this bundle is (or was last) attached to. */
    public String serverInstanceId() { return supervisor.serverInstanceId(); }

    /** The endpoint of the current (or last) session. */
    public BundleClientConfig.Endpoint currentEndpoint() { return supervisor.currentEndpoint(); }

    /** Fail-backs to the preferred endpoint that completed (READY on endpoint 0). */
    public long failbacksCompleted() { return supervisor.failbacksCompleted(); }

    /** Fail-back switches that did not end on the preferred endpoint. */
    public long failbacksFailed() { return supervisor.failbacksFailed(); }

    /** Probes of the preferred endpoint that its broker answered (while attached elsewhere). */
    public long failbackProbesAnswered() { return supervisor.failbackProbesAnswered(); }

    /** Fail-backs started before the window because a request found no handler on the current broker. */
    public long reactiveFailbacks() { return supervisor.reactiveFailbacks(); }

    /** Whether fail-back is suspended after repeated refusals by the preferred broker. */
    public boolean failbackSuspended() { return supervisor.failbackSuspended(); }

    /** Whether the current session is being drained for a fail-back (new requests are refused). */
    public boolean failbackDraining() { return supervisor.failbackDraining(); }

    public void onStateChange(BiConsumer<BundleClientState, BundleClientState> listener) {
        supervisor.onStateChange(listener);
    }

    // ---------- Handler registration ----------

    /**
     * Register a request handler for {@code payloadType}. The bundle must be
     * started AFTER registering handlers (the supervisor uses the configured
     * handler list at registration time).
     */
    public void registerRequestHandler(String payloadType, HandlerRegistry.RequestHandler handler) {
        handlerRegistry.registerRequestHandler(payloadType, handler);
    }

    public void registerNotificationHandler(String payloadType, HandlerRegistry.NotificationHandler handler) {
        handlerRegistry.registerNotificationHandler(payloadType, handler);
    }

    // ---------- Outbound traffic ----------

    /**
     * Issue an RPC. Returns a {@link CompletableFuture} that completes with the
     * server's {@link Response} (which may be a failure response — inspect
     * {@code response.isError()}). The future is also failed exceptionally if
     * the supervisor cannot accept the send (e.g. not yet READY).
     */
    public CompletableFuture<Response> request(String payloadType, byte[] payload, Duration timeout) {
        return request(payloadType, payload, timeout, false);
    }

    /**
     * @param idempotent whether the request is a read that may be executed any number of
     *                   times (a query, an event fetch). Brokers route such reads straight
     *                   and keep no reply for a caller that went away, so an idempotent
     *                   request still pending when its session ends is sent again on the
     *                   next one (same correlation id, the time it has left) rather than
     *                   failing; a request with side effects never is.
     */
    public CompletableFuture<Response> request(String payloadType, byte[] payload, Duration timeout,
                                              boolean idempotent) {
        if (idempotent && answersLocally(payloadType)) {
            return requestLocally(payloadType, payload, timeout);
        }
        int limit = config.maxInFlightRequests();
        if (limit > 0 && correlationTracker.pendingCount() >= limit) {
            // Refuse now rather than add to a backlog nobody can serve in time.
            // Waiting out the timeout costs the caller its whole deadline and
            // still fails; failing in microseconds lets it slow down or shed load
            // while the requests already in flight get a chance to finish.
            return CompletableFuture.failedFuture(new TooManyPendingRequestsException(
                    "refusing " + payloadType + ": " + correlationTracker.pendingCount()
                            + " requests already in flight (max " + limit + ")"));
        }
        var correlationId = UUID.randomUUID();
        long startNanos = System.nanoTime();
        var request = new Request(
                correlationId,
                config.bundleId(), config.instanceId(), config.bundleVersion(),
                payloadType, payload,
                timeout == null ? 0L : timeout.toMillis(),
                System.currentTimeMillis(),
                acceptEncodings(), 0, false
        );
        var future = correlationTracker.track(correlationId, payloadType, timeout,
                supervisor.serverInstanceId(), idempotent ? request : null, sessionEpoch.get());
        try {
            supervisor.send(request);
        } catch (SendFailedException sfe) {
            correlationTracker.complete(Response.failure(correlationId,
                    com.evento.transport.message.ResponseError.of(sfe)));
            return future;
        }
        // A broker that has no handler for this type may simply be the wrong one:
        // after a fail-back of the handlers of a cell, only its preferred broker has
        // them. Let the supervisor move back now rather than after its window (N16).
        future.thenAccept(response -> {
            if (isNoHandler(response)) supervisor.noHandlerOnCurrentBroker(payloadType);
            else if (response != null && !response.isError()) {
                latencies.requestRtt().record(payloadType, System.nanoTime() - startNanos);
            }
        });
        return future;
    }

    /**
     * Whether a read of {@code payloadType} is answered by this bundle itself: it is one of
     * its own queries, the short-circuit is on, and the bundle is enabled. A bundle that is
     * not enabled yet (its projectors still catching up, see
     * {@code @Projector(waitForHeadReached)}) or that was disabled is not served by the
     * broker, and does not serve itself either: the query goes to the broker, which picks
     * an instance that is.
     */
    public boolean answersLocally(String payloadType) {
        return config.localQueryShortCircuit() && enabled
                && localQueryTypes.contains(payloadType) && inboundDispatcher.handles(payloadType);
    }

    /**
     * Same bytes, same handler path as a remote query (decode, handler, interceptors,
     * tracing, handler metrics, encode, decode at the caller), without the broker. The
     * round trip is recorded under the payload type like a remote one.
     */
    private CompletableFuture<Response> requestLocally(String payloadType, byte[] payload, Duration timeout) {
        var correlationId = UUID.randomUUID();
        long startNanos = System.nanoTime();
        var request = new Request(correlationId,
                config.bundleId(), config.instanceId(), config.bundleVersion(),
                payloadType, payload,
                timeout == null ? 0L : timeout.toMillis(),
                System.currentTimeMillis());
        localQueries.increment();
        return inboundDispatcher.answerLocally(request, timeout).whenComplete((response, t) -> {
            long elapsed = System.nanoTime() - startNanos;
            if (response != null && !response.isError()) {
                latencies.requestRtt().record(payloadType, elapsed);
            }
            if (log.isDebugEnabled()) {
                log.debug("event=query_answered_locally payloadType={} correlationId={} elapsedMs={} error={}",
                        payloadType, correlationId, elapsed / 1_000_000.0,
                        response == null || response.isError());
            }
        });
    }

    /**
     * Asks {@code payloadType} for a streamed reply: frames pulled from the returned stream
     * as the caller consumes them, with credit flowing back, so that no hop holds more
     * than {@code window} frames of it. Close the stream (it cancels one that has not ended).
     *
     * <ul>
     *   <li>A query this bundle answers itself streams in process (same handler path, no
     *       broker), see {@link #answersLocally}.</li>
     *   <li>On a session whose broker does not relay streams, or towards a handler that
     *       cannot stream, the reply arrives whole, as one {@link InboundStream.Item.Whole}.</li>
     *   <li>{@code direct}: the handler may offer the stream on a direct connection
     *       ({@link com.evento.transport.message.StreamRedirect}); the caller of this method
     *       handles that through {@link InboundStream#onRedirect}.</li>
     * </ul>
     *
     * @param timeout how long to wait for the first frame, and then between two frames
     */
    public InboundStream openStream(String payloadType, byte[] payload, Duration timeout, int window, boolean direct) {
        var correlationId = UUID.randomUUID();
        long timeoutMs = timeout == null ? 0L : timeout.toMillis();
        if (answersLocally(payloadType)) {
            localQueries.increment();
            var stream = new InboundStream(correlationId, payloadType, window, timeout,
                    inboundDispatcher::controlLocally, id -> { });
            inboundDispatcher.answerLocally(new Request(correlationId,
                    config.bundleId(), config.instanceId(), config.bundleVersion(),
                    payloadType, payload, timeoutMs, System.currentTimeMillis(),
                    java.util.Set.of(), window, false), stream);
            log.debug("event=stream_answered_locally payloadType={} correlationId={}", payloadType, correlationId);
            return stream;
        }
        boolean streams = supervisor.accepted(HandshakeProtocol.CAPABILITY_STREAM);
        var stream = new InboundStream(correlationId, payloadType, window, timeout,
                this::sendInternal, inboundDispatcher::unregisterInbound);
        inboundDispatcher.registerInbound(stream);
        if (streams && direct) {
            // Taken on the direct connection the handler offers, or asked again through
            // the broker when that connection cannot be made.
            stream.onRedirect(redirect -> directClient.follow(stream, redirect,
                    () -> reaskViaBroker(stream, payload, timeout)));
        }
        var request = new Request(correlationId,
                config.bundleId(), config.instanceId(), config.bundleVersion(),
                payloadType, payload, timeoutMs, System.currentTimeMillis(),
                acceptEncodings(), streams ? window : 0, streams && direct);
        try {
            supervisor.send(request);
        } catch (SendFailedException notSent) {
            stream.fail(notSent);
        }
        return stream;
    }

    /**
     * Asks a stream again through the broker, without the direct option, under a new
     * correlation id: its handler offered a direct connection that could not be made
     * (unreachable, refused, ticket expired). The stream had received nothing yet; the
     * query is a read, so asking it again is safe.
     */
    public void reaskViaBroker(InboundStream stream, byte[] payload, Duration timeout) {
        var correlationId = UUID.randomUUID();
        stream.retarget(correlationId, this::sendInternal);
        inboundDispatcher.registerInbound(stream);
        boolean streams = supervisor.accepted(HandshakeProtocol.CAPABILITY_STREAM);
        var request = new Request(correlationId,
                config.bundleId(), config.instanceId(), config.bundleVersion(),
                stream.payloadType(), payload, timeout == null ? 0L : timeout.toMillis(), System.currentTimeMillis(),
                acceptEncodings(), streams ? stream.window() : 0, false);
        try {
            supervisor.send(request);
        } catch (SendFailedException notSent) {
            stream.fail(notSent);
        }
    }

    /** The port of this bundle's direct-stream endpoint, or -1 when it offers none. */
    public int directStreamPort() {
        return directServer == null ? -1 : directServer.boundPort();
    }

    /** Streams this bundle served on direct connections. */
    public long directStreamsServed() {
        return directServer == null ? 0 : directServer.served();
    }

    /** Streams this bundle took on a direct connection another bundle offered. */
    public long directStreamsFollowed() {
        return directClient.followed();
    }

    /** Direct streams offered to this bundle that it had to ask again through the broker. */
    public long directStreamFallbacks() {
        return directClient.fallbacks();
    }

    /** Whether the current session carries streamed replies. */
    public boolean streamsAccepted() {
        return supervisor.accepted(HandshakeProtocol.CAPABILITY_STREAM);
    }

    /**
     * Whether the broker of the current session keeps payload revisions (2.6): payloads of a
     * revised class may only be sent when it does.
     */
    public boolean revisionsAccepted() {
        return supervisor.accepted(HandshakeProtocol.CAPABILITY_REVISION);
    }

    /** Streams this bundle is receiving through the broker now. */
    public int inboundStreams() {
        return inboundDispatcher.inboundStreams();
    }

    /** Streams this bundle's handlers are sending now. */
    public int outboundStreams() {
        return inboundDispatcher.outboundStreams();
    }

    /** Queries this bundle answered itself instead of sending them through the broker. */
    public long localQueries() {
        return localQueries.sum();
    }

    /** The broker's routing miss: nothing on it handles the payload type. */
    static boolean isNoHandler(Response response) {
        if (response == null || !response.isError() || response.error() == null) return false;
        var msg = response.error().message();
        return IllegalStateException.class.getName().equals(response.error().exceptionClassName())
                && msg != null
                && (msg.startsWith("no handler for ") || msg.startsWith("no available handler for "));
    }

    public CompletableFuture<Response> request(String payloadType, byte[] payload) {
        return request(payloadType, payload, config.defaultRequestTimeout());
    }

    /**
     * Fire-and-forget notification. The returned future tracks only the
     * underlying write — the application layer never sees a reply (there is none).
     */
    public CompletableFuture<Void> notify(String payloadType, byte[] payload) {
        var notification = new Notification(
                UUID.randomUUID(), payloadType,
                payload == null ? new byte[0] : payload,
                System.currentTimeMillis());
        return supervisor.send(notification);
    }

    /**
     * Take this bundle in/out of the broker's available view without closing
     * the transport. Useful for graceful drain.
     */
    /**
     * Put this bundle in the broker's available view, now and on every later
     * session. Never fails for want of a connection: the intent is latched first,
     * and when it cannot be written now (between sessions, during a fail-back
     * drain, or the write fails with the connection) the next session's
     * registration sends it. The future completes once it has been written or
     * deferred that way.
     */
    public CompletableFuture<Void> enable() {
        // Mark the supervisor so future reconnect sessions re-send NOTIFY_ENABLE
        // automatically — without this, a reconnected bundle would be registered
        // in ClusterRegistry.handlers but never appear in ConnectionRegistry.enabledView,
        // causing permanent "no handler for X" routing failures.
        supervisor.markEnabled();
        enabled = true;
        try {
            return supervisor.send(new Notification(UUID.randomUUID(),
                    ProtocolNotifications.ENABLE, new byte[0], System.currentTimeMillis()))
                    .exceptionally(t -> {
                        log.info("event=enable_deferred reason=write_failed cause={} "
                                + "- sent by the next session's registration", t.toString());
                        return null;
                    });
        } catch (SendFailedException e) {
            // Startup code calls this once, when its projectors have caught up; if that
            // moment falls between two sessions, throwing here aborted the rest of the
            // startup (sagas, observers, the started hook) for good (N16 review).
            log.info("event=enable_deferred reason=not_sent cause={} "
                    + "- sent by the next session's registration", e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    public CompletableFuture<Void> disable() {
        enabled = false;
        return supervisor.send(new Notification(UUID.randomUUID(),
                ProtocolNotifications.DISABLE, new byte[0], System.currentTimeMillis()));
    }

    // ---------- Internals ----------

    private static final java.util.Set<String> DEFLATE_ONLY =
            java.util.Set.of(com.evento.transport.codec.PayloadEncoding.DEFLATE);

    /** What this bundle's requests accept in their replies. */
    java.util.Set<String> acceptEncodings() {
        return config.acceptCompressedReplies() ? DEFLATE_ONLY : java.util.Set.of();
    }

    private CompletableFuture<Void> sendInternal(com.evento.transport.message.Message m) {
        return supervisor.send(m);
    }

    /**
     * The payload types handled by {@code @QueryHandler}s. Queries are read-only
     * and idempotent, so they bypass the exactly-once {@link ProcessedRequestCache}:
     * deduplicating them buys nothing and would pin their (potentially large)
     * responses in memory.
     */
    private static java.util.Set<String> queryPayloadTypes(BundleClientConfig config) {
        return java.util.Set.copyOf(config.queryPayloadTypes());
    }

    public PayloadCodec payloadCodec() { return payloadCodec; }
    public BundleClientConfig config() { return config; }

    public int pendingOutboundCount() { return correlationTracker.pendingCount(); }

    /**
     * Latencies this bundle measured itself: the round trip of its requests by payload type,
     * and its handlers' execution time by handler type (recorded by the request handlers that
     * use it, e.g. {@code BundleInboundDispatcher}).
     */
    public com.evento.application.client.metrics.BundleLatencies latencies() { return latencies; }
    public int processedRequestCacheSize() { return processedRequestCache.size(); }

    /**
     * Test-only accessor — returns the live {@code NettyClientTransport} the
     * supervisor is currently using. Useful for forcing a disconnect from
     * outside to exercise reconnect paths. Never use from application code:
     * application code talks through {@link #request}/{@link #notify}.
     */
    public com.evento.transport.netty.NettyClientTransport currentTransportForTest() {
        return supervisor.currentTransport();
    }

    // ---------- Builder ----------

    public static Builder builder(String bundleId, String instanceId) {
        return new Builder(bundleId, instanceId);
    }

    public static final class Builder {
        private final BundleClientConfig.Builder cfg;
        private PayloadCodec payloadCodec = new JacksonCborPayloadCodec();

        private Builder(String bundleId, String instanceId) {
            this.cfg = BundleClientConfig.builder().bundleId(bundleId).instanceId(instanceId);
        }

        public Builder host(String host) { cfg.host(host); return this; }
        public Builder port(int port) { cfg.port(port); return this; }
        /** Append a broker to the failover list; the first added is the preferred one. */
        public Builder endpoint(String host, int port) { cfg.endpoint(host, port); return this; }
        public Builder endpoints(java.util.List<BundleClientConfig.Endpoint> endpoints) { cfg.endpoints(endpoints); return this; }
        public Builder bundleVersion(String v) { cfg.bundleVersion(v); return this; }
        public Builder authToken(String t) { cfg.authToken(t); return this; }
        public Builder description(String d) { cfg.description(d); return this; }
        public Builder detail(String d) { cfg.detail(d); return this; }
        public Builder repositoryUrl(String url) { cfg.repositoryUrl(url); return this; }
        public Builder linePrefix(String p) { cfg.linePrefix(p); return this; }
        public Builder handlerPayloadTypes(java.util.List<String> types) { cfg.handlerPayloadTypes(types); return this; }
        public Builder registeredHandlers(java.util.List<com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler> handlers) { cfg.registeredHandlers(handlers); return this; }
        public Builder payloadInfo(java.util.Map<String, com.evento.transport.protocol.PayloadDiscoveryInfo> info) { cfg.payloadInfo(info); return this; }
        public Builder capabilities(java.util.Set<String> caps) { cfg.capabilities(caps); return this; }
        /** Advertise where this bundle runs ({@code zone:<ZONE>} in Hello); null clears it. */
        public Builder zone(String zone) { cfg.zone(zone); return this; }
        public Builder handshakeTimeout(Duration d) { cfg.handshakeTimeout(d); return this; }
        public Builder defaultRequestTimeout(Duration d) { cfg.defaultRequestTimeout(d); return this; }
        /** Cap on outstanding requests; 0 disables the bound. */
        public Builder maxInFlightRequests(int n) { cfg.maxInFlightRequests(n); return this; }
        public Builder transportConfig(com.evento.transport.netty.NettyTransportConfig c) { cfg.transportConfig(c); return this; }
        public Builder payloadCodec(PayloadCodec c) { this.payloadCodec = c; return this; }
        public Builder autoEnable(boolean v) { cfg.autoEnable(v); return this; }
        /** Refuse any broker whose announced instance id is not this one; null accepts all. */
        public Builder expectedServerInstanceId(String id) { cfg.expectedServerInstanceId(id); return this; }
        /**
         * Refuse any broker that does not announce this cluster name; null accepts all.
         * Use this rather than {@link #expectedServerInstanceId} with several endpoints.
         */
        public Builder expectedClusterName(String name) { cfg.expectedClusterName(name); return this; }
        /** Move back to the first endpoint once it is healthy again (default true, needs 2+ endpoints). */
        public Builder failbackEnabled(boolean v) { cfg.failbackEnabled(v); return this; }
        public Builder failbackProbeInterval(Duration d) { cfg.failbackProbeInterval(d); return this; }
        public Builder failbackStabilityWindow(Duration d) { cfg.failbackStabilityWindow(d); return this; }
        public Builder failbackJitter(Duration d) { cfg.failbackJitter(d); return this; }
        public Builder failbackDrainTimeout(Duration d) { cfg.failbackDrainTimeout(d); return this; }
        /** Compress this bundle's replies of at least this many bytes for callers that accept it; 0 never. */
        public Builder compressionThreshold(int bytes) { cfg.compressionThreshold(bytes); return this; }
        /** Whether this bundle's requests accept compressed replies (default true). */
        public Builder acceptCompressedReplies(boolean v) { cfg.acceptCompressedReplies(v); return this; }
        /** Answer this bundle's own queries locally once it is enabled (default true). */
        public Builder localQueryShortCircuit(boolean v) { cfg.localQueryShortCircuit(v); return this; }
        /** Offer streamed answers on direct connections (null: through the broker only). */
        public Builder directStreams(com.evento.application.client.stream.DirectStreamConfig c) { cfg.directStreams(c); return this; }

        public BundleClient build() {
            return new BundleClient(cfg.build(), payloadCodec);
        }
    }
}
