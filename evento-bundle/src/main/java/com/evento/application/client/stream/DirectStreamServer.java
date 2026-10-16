package com.evento.application.client.stream;

import com.evento.application.client.handler.HandlerRegistry;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.SendFailedException;
import com.evento.transport.Transport;
import com.evento.transport.message.Hello;
import com.evento.transport.message.Message;
import com.evento.transport.message.Reject;
import com.evento.transport.message.Request;
import com.evento.transport.message.StreamCancel;
import com.evento.transport.message.StreamCredit;
import com.evento.transport.message.StreamRedirect;
import com.evento.transport.message.Welcome;
import com.evento.transport.netty.NettyServerTransport;
import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.state.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The answering bundle's endpoint for streams taken on a direct connection (2.6).
 *
 * <p>It serves streams, nothing else, and only to whoever holds a ticket:
 * <ol>
 *   <li>A streamed query arrives through the broker with {@code streamDirect}; instead of
 *       streaming through the broker, the bundle {@link #offer offers} it here: a random,
 *       single-use ticket bound to the query's correlation id, valid for the ticket TTL,
 *       sent back through the broker in a {@link StreamRedirect}.</li>
 *   <li>The caller connects and sends a {@link Hello} carrying the ticket (as its token) and
 *       the correlation id. A wrong, used or expired ticket, or one presented for another
 *       stream, is refused ({@link Reject}) and the connection closed.</li>
 *   <li>Accepted, the query runs exactly as it would for the broker (same handler path),
 *       its frames written on this connection under the same credit discipline.</li>
 * </ol>
 * The broker authenticated both bundles and chose this one; the ticket, delivered over the
 * broker connections, carries that decision to the direct connection. The caller closes
 * the connection when the stream ends; a connection that closes first cancels the stream.
 */
public final class DirectStreamServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DirectStreamServer.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Runs a ticketed stream on its connection: the request, its handler, where frames go. */
    @FunctionalInterface
    public interface StreamRunner {
        CompletableFuture<Void> run(Request request, HandlerRegistry.StreamingRequestHandler handler,
                                    Function<Message, CompletableFuture<Void>> frames);
    }

    private record Pending(Request request, HandlerRegistry.StreamingRequestHandler handler, long expiresAtMs) {}

    private final DirectStreamConfig config;
    private final NettyTransportConfig transportConfig;
    private final String instanceId;
    private final StreamRunner runner;
    private final Consumer<Message> control;
    private final ConcurrentHashMap<String, Pending> tickets = new ConcurrentHashMap<>();
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicLong served = new AtomicLong();
    private final AtomicLong refused = new AtomicLong();
    private final AtomicLong expired = new AtomicLong();
    private volatile NettyServerTransport server;
    private volatile ScheduledExecutorService sweeper;
    private volatile int boundPort;

    /**
     * @param transportConfig the bundle's transport settings (codec, heartbeat, frame size);
     *                        TLS comes from {@code config}
     * @param runner          runs a stream (the bundle's dispatcher)
     * @param control         where credit and cancel of a direct stream go (the dispatcher)
     */
    public DirectStreamServer(DirectStreamConfig config, NettyTransportConfig transportConfig, String instanceId,
                              StreamRunner runner, Consumer<Message> control) {
        this.config = config;
        this.transportConfig = config.serverSslContext() == null
                ? transportConfig.withSslContext(null) : transportConfig.withSslContext(config.serverSslContext());
        this.instanceId = instanceId;
        this.runner = runner;
        this.control = control;
    }

    /** Binds the endpoint. */
    public synchronized int start() {
        if (server != null) return boundPort;
        var transport = new NettyServerTransport(transportConfig);
        transport.onConnection(this::accept);
        boundPort = transport.start(config.port());
        server = transport;
        var sweep = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "evento-direct-stream-tickets");
            t.setDaemon(true);
            return t;
        });
        long every = Math.max(1_000L, config.ticketTtl().toMillis());
        sweep.scheduleWithFixedDelay(this::sweepExpired, every, every, TimeUnit.MILLISECONDS);
        sweeper = sweep;
        log.info("event=direct_stream_endpoint_started port={} advertised={}:{} tls={}",
                boundPort, config.advertisedHost(), advertisedPort(), config.serverSslContext() != null);
        return boundPort;
    }

    public int boundPort() {
        return boundPort;
    }

    private int advertisedPort() {
        return config.advertisedPort() > 0 ? config.advertisedPort() : boundPort;
    }

    /**
     * Offers the stream {@code request} asks for on this endpoint: a ticket for it, in the
     * redirect to send back to the caller through the broker.
     */
    public StreamRedirect offer(Request request, HandlerRegistry.StreamingRequestHandler handler) {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long now = System.currentTimeMillis();
        long expiresAt = now + config.ticketTtl().toMillis();
        tickets.put(ticket, new Pending(request, handler, expiresAt));
        return new StreamRedirect(request.correlationId(), config.advertisedHost(), advertisedPort(), ticket,
                expiresAt, now);
    }

    private void accept(Transport transport) {
        var stream = new AtomicReference<UUID>();
        transport.onMessage(message -> {
            switch (message) {
                case Hello hello -> onHello(transport, hello, stream);
                case StreamCredit credit when credit.correlationId().equals(stream.get()) -> control.accept(credit);
                case StreamCancel cancel when cancel.correlationId().equals(stream.get()) -> control.accept(cancel);
                default -> log.debug("event=direct_stream_frame_ignored type={}", message.getClass().getSimpleName());
            }
        });
        transport.onStateChange((from, to) -> {
            var id = stream.get();
            if (id != null && (to == ConnectionState.DISCONNECTED || to == ConnectionState.CLOSED)) {
                // Stops a stream still running (no-op for one that ended).
                control.accept(StreamCancel.of(id, "direct connection closed"));
            }
        });
    }

    private void onHello(Transport transport, Hello hello, AtomicReference<UUID> stream) {
        if (stream.get() != null) return;  // one stream per connection
        var pending = hello.authToken() == null ? null : tickets.remove(hello.authToken());
        String refusal = null;
        if (pending == null) refusal = "unknown or already used ticket";
        else if (System.currentTimeMillis() > pending.expiresAtMs()) refusal = "ticket expired";
        else if (!pending.request().correlationId().equals(hello.correlationId())) refusal = "ticket issued for another stream";
        else if (hello.protocolVersion() != HandshakeProtocol.PROTOCOL_VERSION) refusal = "unsupported protocol version";
        if (refusal != null) {
            refused.incrementAndGet();
            log.warn("event=direct_stream_refused bundle={} instance={} reason=\"{}\"",
                    hello.bundleId(), hello.instanceId(), refusal);
            try {
                transport.send(new Reject(hello.correlationId(), Reject.CODE_AUTH_FAILED, refusal, System.currentTimeMillis()))
                        .whenComplete((v, t) -> transport.close());
            } catch (SendFailedException e) {
                transport.close();
            }
            return;
        }
        var id = pending.request().correlationId();
        stream.set(id);
        try {
            transport.send(new Welcome(hello.correlationId(), HandshakeProtocol.PROTOCOL_VERSION, "direct:" + instanceId,
                    Set.of(HandshakeProtocol.CAPABILITY_STREAM), System.currentTimeMillis()));
        } catch (SendFailedException e) {
            transport.close();
            return;
        }
        served.incrementAndGet();
        log.debug("event=direct_stream_started correlationId={} payloadType={} caller={}",
                id, pending.request().payloadType(), hello.instanceId());
        executor.execute(() -> runner.run(pending.request(), pending.handler(), transport::send));
    }

    private void sweepExpired() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> {
            if (now <= e.getValue().expiresAtMs()) return false;
            expired.incrementAndGet();
            log.info("event=direct_stream_ticket_expired correlationId={} payloadType={}",
                    e.getValue().request().correlationId(), e.getValue().request().payloadType());
            return true;
        });
    }

    /** Streams served on direct connections since start. */
    public long served() { return served.get(); }
    /** Connections refused (bad, used, expired or foreign ticket). */
    public long refused() { return refused.get(); }
    /** Tickets that expired unused (their caller fell back to the broker, or left). */
    public long expiredTickets() { return expired.get(); }

    @Override
    public synchronized void close() {
        var s = server;
        server = null;
        if (s != null) s.stop();
        var sweep = sweeper;
        sweeper = null;
        if (sweep != null) sweep.shutdownNow();
        tickets.clear();
    }
}
