package com.evento.application.client;

import com.evento.application.client.connection.ConnectionSupervisor;
import com.evento.application.client.correlation.BundleCorrelationTracker;
import com.evento.application.client.dedup.ProcessedRequestCache;
import com.evento.application.client.handler.HandlerRegistry;
import com.evento.transport.SendFailedException;
import com.evento.transport.codec.JacksonCborPayloadCodec;
import com.evento.transport.codec.PayloadCodec;
import com.evento.transport.message.Notification;
import com.evento.transport.message.Request;
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
        this.inboundDispatcher = new InboundDispatcher(
                correlationTracker,
                processedRequestCache,
                handlerRegistry,
                config.transportConfig().businessExecutor(),
                this::sendInternal
        );
        this.supervisor.setInboundSink(inboundDispatcher.asMessageSink());
    }

    // ---------- Lifecycle ----------

    /**
     * Open the connection and complete the handshake/registration. Returns when
     * the bundle is READY (or when the first attempt fails).
     */
    public CompletableFuture<Void> start() {
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
        }
    }

    @Override
    public void close() {
        stop(config.shutdownDeadline());
    }

    public BundleClientState state() { return supervisor.state(); }

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
        var correlationId = UUID.randomUUID();
        var future = correlationTracker.track(correlationId, timeout);
        var request = new Request(
                correlationId,
                config.bundleId(), config.instanceId(), config.bundleVersion(),
                payloadType, payload,
                timeout == null ? 0L : timeout.toMillis(),
                System.currentTimeMillis()
        );
        try {
            supervisor.send(request);
        } catch (SendFailedException sfe) {
            correlationTracker.complete(Response.failure(correlationId,
                    com.evento.transport.message.ResponseError.of(sfe)));
        }
        return future;
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
    public CompletableFuture<Void> enable() {
        // Mark the supervisor so future reconnect sessions re-send NOTIFY_ENABLE
        // automatically — without this, a reconnected bundle would be registered
        // in ClusterRegistry.handlers but never appear in ConnectionRegistry.enabledView,
        // causing permanent "no handler for X" routing failures.
        supervisor.markEnabled();
        return supervisor.send(new Notification(UUID.randomUUID(),
                ProtocolNotifications.ENABLE, new byte[0], System.currentTimeMillis()));
    }

    public CompletableFuture<Void> disable() {
        return supervisor.send(new Notification(UUID.randomUUID(),
                ProtocolNotifications.DISABLE, new byte[0], System.currentTimeMillis()));
    }

    // ---------- Internals ----------

    private CompletableFuture<Void> sendInternal(com.evento.transport.message.Message m) {
        return supervisor.send(m);
    }

    public PayloadCodec payloadCodec() { return payloadCodec; }
    public BundleClientConfig config() { return config; }

    public int pendingOutboundCount() { return correlationTracker.pendingCount(); }
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
        public Builder bundleVersion(String v) { cfg.bundleVersion(v); return this; }
        public Builder authToken(String t) { cfg.authToken(t); return this; }
        public Builder handlerPayloadTypes(java.util.List<String> types) { cfg.handlerPayloadTypes(types); return this; }
        public Builder registeredHandlers(java.util.List<com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler> handlers) { cfg.registeredHandlers(handlers); return this; }
        public Builder payloadInfo(java.util.Map<String, String[]> info) { cfg.payloadInfo(info); return this; }
        public Builder capabilities(java.util.Set<String> caps) { cfg.capabilities(caps); return this; }
        public Builder handshakeTimeout(Duration d) { cfg.handshakeTimeout(d); return this; }
        public Builder defaultRequestTimeout(Duration d) { cfg.defaultRequestTimeout(d); return this; }
        public Builder transportConfig(com.evento.transport.netty.NettyTransportConfig c) { cfg.transportConfig(c); return this; }
        public Builder payloadCodec(PayloadCodec c) { this.payloadCodec = c; return this; }
        public Builder autoEnable(boolean v) { cfg.autoEnable(v); return this; }

        public BundleClient build() {
            return new BundleClient(cfg.build(), payloadCodec);
        }
    }
}
