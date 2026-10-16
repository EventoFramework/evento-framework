package com.evento.application.client.stream;

import com.evento.transport.HandshakeProtocol;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Reject;
import com.evento.transport.message.Response;
import com.evento.transport.message.StreamCancel;
import com.evento.transport.message.StreamData;
import com.evento.transport.message.StreamRedirect;
import com.evento.transport.message.Welcome;
import com.evento.transport.netty.NettyClientTransport;
import com.evento.transport.netty.NettyEventLoops;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.state.ConnectionState;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The caller's side of streams taken on a direct connection (2.6): follows a
 * {@link StreamRedirect} to the answering bundle's endpoint and feeds the frames it reads
 * there into the same {@link InboundStream} the application is reading.
 *
 * <p>Whatever goes wrong before the stream is established — the endpoint is unreachable
 * from here, TLS does not match, the ticket expired or was refused — the stream is asked
 * again through the broker (the {@code fallback}), so asking for a direct stream never
 * costs more than one failed connection attempt. Once established, losing the connection
 * fails the stream, exactly as losing the broker session does.
 *
 * <p>Connections share one event loop group, created on first use.
 */
public final class DirectStreamClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DirectStreamClient.class);

    private final NettyTransportConfig transportConfig;
    private final String bundleId;
    private final String instanceId;
    private final String bundleVersion;
    private final Duration handshakeTimeout;
    private final AtomicLong followed = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();
    private volatile EventLoopGroup group;

    /**
     * @param transportConfig the bundle's broker transport settings; its TLS context (a
     *                        client one, or none) is used for direct connections too
     */
    public DirectStreamClient(NettyTransportConfig transportConfig, String bundleId, String instanceId,
                              String bundleVersion, Duration handshakeTimeout) {
        this.transportConfig = transportConfig;
        this.bundleId = bundleId;
        this.instanceId = instanceId;
        this.bundleVersion = bundleVersion;
        this.handshakeTimeout = handshakeTimeout == null ? Duration.ofSeconds(5) : handshakeTimeout;
    }

    /**
     * Takes {@code stream} on the connection {@code redirect} offers; on any failure to
     * establish it, runs {@code fallback} (which asks the stream again through the broker).
     * Runs on the thread reading the stream: it blocks for at most the connect timeout plus
     * the handshake timeout.
     */
    public void follow(InboundStream stream, StreamRedirect redirect, Runnable fallback) {
        NettyClientTransport transport = null;
        try {
            if (redirect.expiresAtMs() > 0 && System.currentTimeMillis() > redirect.expiresAtMs()) {
                throw new IllegalStateException("the ticket expired before it could be used");
            }
            transport = new NettyClientTransport("direct-stream:" + redirect.host() + ":" + redirect.port(),
                    redirect.host(), redirect.port(), transportConfig, group());
            var handshake = new CompletableFuture<Welcome>();
            var established = new java.util.concurrent.atomic.AtomicBoolean();
            transport.onMessage(message -> {
                switch (message) {
                    case Welcome welcome -> handshake.complete(welcome);
                    case Reject reject -> handshake.completeExceptionally(
                            new IllegalStateException("refused by the endpoint: " + reject.reason()));
                    case StreamData data -> stream.accept(data);
                    case Response response -> stream.accept(response);
                    case StreamCancel cancel -> stream.fail(new IllegalStateException("stream cancelled: " + cancel.reason()));
                    default -> { }
                }
            });
            transport.onStateChange((from, to) -> {
                // Only once established: before that, a failed attempt falls back instead.
                if (established.get() && (to == ConnectionState.DISCONNECTED || to == ConnectionState.CLOSED)) {
                    stream.fail(new IllegalStateException("the direct connection to " + redirect.host() + ":"
                            + redirect.port() + " was lost"));
                }
            });
            long connectMs = transportConfig.connectTimeout().toMillis() + 1_000;
            transport.connect().get(connectMs, TimeUnit.MILLISECONDS);
            transport.send(new Hello(stream.correlationId(), HandshakeProtocol.PROTOCOL_VERSION, bundleId, instanceId,
                    bundleVersion, Set.of(HandshakeProtocol.CAPABILITY_STREAM), redirect.ticket(),
                    System.currentTimeMillis()));
            handshake.get(handshakeTimeout.toMillis(), TimeUnit.MILLISECONDS);
            final var connected = transport;
            stream.switchControl(connected::send);
            stream.onFinish(connected::close);
            established.set(true);
            followed.incrementAndGet();
            log.debug("event=direct_stream_followed correlationId={} endpoint={}:{}",
                    stream.correlationId(), redirect.host(), redirect.port());
        } catch (Exception e) {
            if (transport != null) transport.close();
            fallbacks.incrementAndGet();
            var cause = e instanceof java.util.concurrent.ExecutionException ee && ee.getCause() != null ? ee.getCause() : e;
            log.warn("event=direct_stream_fallback correlationId={} endpoint={}:{} cause=\"{}\" "
                            + "- asking the stream again through the broker",
                    stream.correlationId(), redirect.host(), redirect.port(), cause.toString());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            fallback.run();
        }
    }

    /** Streams taken on a direct connection. */
    public long followed() { return followed.get(); }
    /** Redirects that could not be followed and went back to the broker. */
    public long fallbacks() { return fallbacks.get(); }

    private EventLoopGroup group() {
        var g = group;
        if (g == null) {
            synchronized (this) {
                g = group;
                if (g == null) {
                    g = NettyEventLoops.newNioEventLoopGroup(0, r -> {
                        var t = new Thread(r, "evento-direct-stream-io");
                        t.setDaemon(true);
                        return t;
                    });
                    group = g;
                }
            }
        }
        return g;
    }

    @Override
    public synchronized void close() {
        var g = group;
        group = null;
        if (g != null) g.shutdownGracefully(0, 1, TimeUnit.SECONDS);
    }
}
