package com.evento.transport.netty;

import com.evento.transport.Frame;
import com.evento.transport.SendFailedException;
import com.evento.transport.Transport;
import com.evento.transport.TransportException;
import com.evento.transport.message.Message;
import com.evento.transport.state.ConnectionState;
import com.evento.transport.state.ConnectionStateMachine;
import io.netty.buffer.Unpooled;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Client-side {@link Transport} backed by a single Netty channel. Reconnect is
 * handled by a higher-level managed connection; this class focuses on one
 * connection lifecycle: connect, send/receive, close.
 *
 * <p>Thread-safety: {@code send}, {@code state}, and listener registration are
 * safe to call from multiple threads. {@code connect}/{@code close} are expected
 * to be sequenced by a single supervisor.
 */
public final class NettyClientTransport implements Transport {

    private static final Logger log = LoggerFactory.getLogger(NettyClientTransport.class);

    private final String remoteId;
    private final String host;
    private final int port;
    private final NettyTransportConfig config;
    private final ConnectionStateMachine state;
    private final EventoPipelineFactory pipelineFactory;
    private final AtomicLong lastInboundMs = new AtomicLong(0L);

    private final EventLoopGroup workerGroup;
    private final boolean ownsWorkerGroup;
    private volatile Channel channel;
    private volatile Consumer<Frame> frameHandler = f -> {};

    public NettyClientTransport(String remoteId, String host, int port, NettyTransportConfig config) {
        this(remoteId, host, port, config, null);
    }

    public NettyClientTransport(String remoteId, String host, int port,
                                NettyTransportConfig config, EventLoopGroup sharedWorkerGroup) {
        this.remoteId = Objects.requireNonNull(remoteId);
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.config = Objects.requireNonNull(config);
        this.state = new ConnectionStateMachine("client:" + remoteId);
        this.pipelineFactory = new EventoPipelineFactory(config);
        if (sharedWorkerGroup != null) {
            this.workerGroup = sharedWorkerGroup;
            this.ownsWorkerGroup = false;
        } else {
            this.workerGroup = new NioEventLoopGroup();
            this.ownsWorkerGroup = true;
        }
    }

    @Override public String remoteId() { return remoteId; }
    @Override public ConnectionState state() { return state.current(); }
    @Override public long lastInboundMs() { return lastInboundMs.get(); }

    @Override
    public CompletableFuture<Void> connect() {
        if (!state.compareAndTransition(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING, "connect()")) {
            var current = state.current();
            if (current == ConnectionState.CONNECTED || current == ConnectionState.HANDSHAKING) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.failedFuture(
                    new TransportException("cannot connect from state " + current));
        }

        var bootstrap = new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, config.writeBufferHighWaterMark())
                .option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, config.writeBufferLowWaterMark())
                .handler(new io.netty.channel.ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel ch) {
                        pipelineFactory.configure(ch, state, lastInboundMs, f -> frameHandler.accept(f));
                    }
                });

        var promise = new CompletableFuture<Void>();
        bootstrap.connect(host, port).addListener((io.netty.channel.ChannelFuture cf) -> {
            if (cf.isSuccess()) {
                channel = cf.channel();
                state.compareAndTransition(ConnectionState.CONNECTING, ConnectionState.HANDSHAKING, "tcp_established");
                // Application-level handshake is performed by the layer above (Hello/Welcome message exchange).
                // For now we mark the connection as CONNECTED; the handshake supervisor will downgrade
                // the state to DEGRADED if the Hello/Welcome handshake fails.
                state.compareAndTransition(ConnectionState.HANDSHAKING, ConnectionState.CONNECTED, "tcp_ready");
                channel.closeFuture().addListener(closeFuture -> {
                    if (state.current() != ConnectionState.CLOSED) {
                        state.forceTransition(ConnectionState.DISCONNECTED, "channel_closed");
                    }
                });
                log.info("event=client_connected remote={} host={} port={}", remoteId, host, port);
                promise.complete(null);
            } else {
                state.forceTransition(ConnectionState.DISCONNECTED, "connect_failed");
                log.warn("event=client_connect_failed remote={} host={} port={} cause={}",
                        remoteId, host, port, cf.cause().toString());
                promise.completeExceptionally(cf.cause());
            }
        });
        return promise;
    }

    @Override
    public CompletableFuture<Void> send(Message message) {
        Objects.requireNonNull(message, "message");
        var snapshot = state.current();
        if (!snapshot.canSend()) {
            throw new SendFailedException("not in CONNECTED state: " + snapshot, snapshot);
        }
        var ch = channel;
        if (ch == null || !ch.isActive()) {
            throw new SendFailedException("channel not active", snapshot);
        }
        var future = new CompletableFuture<Void>();
        ch.writeAndFlush(message).addListener(cf -> {
            if (cf.isSuccess()) {
                future.complete(null);
            } else {
                future.completeExceptionally(new SendFailedException(
                        "write failed: " + cf.cause().getMessage(),
                        state.current(), cf.cause()));
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> sendRaw(byte[] frameBytes) {
        Objects.requireNonNull(frameBytes, "frameBytes");
        var snapshot = state.current();
        if (!snapshot.canSend()) {
            throw new SendFailedException("not in CONNECTED state: " + snapshot, snapshot);
        }
        var ch = channel;
        if (ch == null || !ch.isActive()) {
            throw new SendFailedException("channel not active", snapshot);
        }
        // Write raw bytes as a ByteBuf. The pipeline's CborMessageEncoder is
        // a MessageToByteEncoder<Message> — non-Message types pass through
        // unchanged; LengthFieldPrepender adds the 4-byte length prefix.
        var future = new CompletableFuture<Void>();
        ch.writeAndFlush(Unpooled.wrappedBuffer(frameBytes)).addListener(cf -> {
            if (cf.isSuccess()) {
                future.complete(null);
            } else {
                future.completeExceptionally(new SendFailedException(
                        "raw write failed: " + cf.cause().getMessage(),
                        state.current(), cf.cause()));
            }
        });
        return future;
    }

    @Override
    public void onMessage(Consumer<Message> handler) {
        Objects.requireNonNull(handler, "handler");
        this.frameHandler = frame -> handler.accept(frame.message());
    }

    @Override
    public void onFrame(Consumer<Frame> handler) {
        this.frameHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public void onStateChange(BiConsumer<ConnectionState, ConnectionState> listener) {
        state.addListener(listener);
    }

    @Override
    public void close() {
        state.forceTransition(ConnectionState.CLOSING, "close()");
        try {
            var ch = channel;
            if (ch != null) {
                ch.close().syncUninterruptibly();
            }
        } finally {
            if (ownsWorkerGroup) {
                workerGroup.shutdownGracefully();
            }
            state.forceTransition(ConnectionState.CLOSED, "closed");
            log.info("event=client_closed remote={}", remoteId);
        }
    }
}
