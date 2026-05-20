package com.evento.transport.netty;

import com.evento.transport.SendFailedException;
import com.evento.transport.Transport;
import com.evento.transport.TransportException;
import com.evento.transport.TransportServer;
import com.evento.transport.message.Message;
import com.evento.transport.state.ConnectionState;
import com.evento.transport.state.ConnectionStateMachine;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Netty-backed server endpoint. Binds, accepts connections, and exposes each
 * accepted client as a {@link Transport}.
 *
 * <p>Each accepted child transport's pipeline is the same as the client's
 * (length-framed CBOR + heartbeat + backpressure + message dispatch), so a
 * client-server pair shares the wire format symmetrically.
 */
public final class NettyServerTransport implements TransportServer {

    private static final Logger log = LoggerFactory.getLogger(NettyServerTransport.class);

    private final NettyTransportConfig config;
    private final EventoPipelineFactory pipelineFactory;
    private final ChannelGroup childChannels =
            new DefaultChannelGroup("evento-server-children", GlobalEventExecutor.INSTANCE);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private Consumer<Transport> connectionHandler;
    private volatile int boundPort = -1;

    public NettyServerTransport(NettyTransportConfig config) {
        this.config = Objects.requireNonNull(config);
        this.pipelineFactory = new EventoPipelineFactory(config);
    }

    @Override
    public void onConnection(Consumer<Transport> handler) {
        if (serverChannel != null) {
            throw new IllegalStateException("onConnection must be called before start()");
        }
        this.connectionHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public int start(int port) {
        if (connectionHandler == null) {
            throw new IllegalStateException("onConnection must be set before start()");
        }
        if (serverChannel != null) {
            throw new IllegalStateException("already started");
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        var bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, config.writeBufferHighWaterMark())
                .childOption(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, config.writeBufferLowWaterMark())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel ch) {
                        var childState = new ConnectionStateMachine("server:" + ch.remoteAddress());
                        // TCP is established as soon as initChannel runs.
                        childState.compareAndTransition(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING, "accept");
                        childState.compareAndTransition(ConnectionState.CONNECTING, ConnectionState.HANDSHAKING, "tcp_established");
                        childState.compareAndTransition(ConnectionState.HANDSHAKING, ConnectionState.CONNECTED, "tcp_ready");
                        var lastInboundMs = new AtomicLong(System.currentTimeMillis());
                        var child = new ServerChildTransport(ch, childState, lastInboundMs, config);
                        pipelineFactory.configure(ch, childState, lastInboundMs, child::deliver);
                        childChannels.add(ch);
                        ch.closeFuture().addListener(cf -> {
                            childChannels.remove(ch);
                            if (childState.current() != ConnectionState.CLOSED) {
                                childState.forceTransition(ConnectionState.DISCONNECTED, "child_channel_closed");
                            }
                        });
                        try {
                            connectionHandler.accept(child);
                        } catch (Throwable t) {
                            log.error("event=connection_handler_failure remote={}", ch.remoteAddress(), t);
                            ch.close();
                        }
                    }
                });

        try {
            serverChannel = bootstrap.bind(port).sync().channel();
            var addr = (InetSocketAddress) serverChannel.localAddress();
            boundPort = addr.getPort();
            log.info("event=server_started port={}", boundPort);
            return boundPort;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("interrupted while binding", e);
        }
    }

    @Override
    public int boundPort() {
        return boundPort;
    }

    @Override
    public void stop() {
        if (serverChannel == null) return;
        try {
            childChannels.close().awaitUninterruptibly(config.connectTimeout().toMillis());
            serverChannel.close().syncUninterruptibly();
        } finally {
            if (workerGroup != null) workerGroup.shutdownGracefully();
            if (bossGroup != null) bossGroup.shutdownGracefully();
            serverChannel = null;
            boundPort = -1;
            log.info("event=server_stopped");
        }
    }

    /**
     * Per-connection {@link Transport} adapter for the server side.
     * Wraps a single accepted {@link io.netty.channel.Channel}.
     */
    private static final class ServerChildTransport implements Transport {
        private final Channel channel;
        private final ConnectionStateMachine state;
        private final AtomicLong lastInboundMs;
        private final NettyTransportConfig config;
        private volatile Consumer<com.evento.transport.Frame> frameHandler = f -> {};

        ServerChildTransport(Channel channel, ConnectionStateMachine state,
                             AtomicLong lastInboundMs, NettyTransportConfig config) {
            this.channel = channel;
            this.state = state;
            this.lastInboundMs = lastInboundMs;
            this.config = config;
        }

        void deliver(com.evento.transport.Frame frame) {
            frameHandler.accept(frame);
        }

        @Override public String remoteId() { return String.valueOf(channel.remoteAddress()); }
        @Override public ConnectionState state() { return state.current(); }
        @Override public long lastInboundMs() { return lastInboundMs.get(); }
        @Override public CompletableFuture<Void> connect() {
            // Server-side children are already connected upon construction.
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> send(Message message) {
            Objects.requireNonNull(message, "message");
            var snapshot = state.current();
            if (!snapshot.canSend()) {
                throw new SendFailedException("not in CONNECTED state: " + snapshot, snapshot);
            }
            if (!channel.isActive()) {
                throw new SendFailedException("child channel not active", snapshot);
            }
            var future = new CompletableFuture<Void>();
            channel.writeAndFlush(message).addListener(cf -> {
                if (cf.isSuccess()) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(new SendFailedException(
                            "child write failed: " + cf.cause().getMessage(),
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
            if (!channel.isActive()) {
                throw new SendFailedException("child channel not active", snapshot);
            }
            // Pre-encoded frame: ByteBuf passes through the encoder (which only
            // handles Message) and gets length-prefixed by LengthFieldPrepender.
            var future = new CompletableFuture<Void>();
            channel.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(frameBytes))
                    .addListener(cf -> {
                        if (cf.isSuccess()) {
                            future.complete(null);
                        } else {
                            future.completeExceptionally(new SendFailedException(
                                    "child raw write failed: " + cf.cause().getMessage(),
                                    state.current(), cf.cause()));
                        }
                    });
            return future;
        }

        @Override public void onMessage(Consumer<Message> handler) {
            Objects.requireNonNull(handler, "handler");
            this.frameHandler = frame -> handler.accept(frame.message());
        }

        @Override public void onFrame(Consumer<com.evento.transport.Frame> handler) {
            this.frameHandler = Objects.requireNonNull(handler, "handler");
        }

        @Override public void onStateChange(BiConsumer<ConnectionState, ConnectionState> listener) {
            state.addListener(listener);
        }

        @Override
        public void close() {
            state.forceTransition(ConnectionState.CLOSING, "child_close");
            try {
                if (channel.isActive()) {
                    channel.close().syncUninterruptibly();
                }
            } finally {
                state.forceTransition(ConnectionState.CLOSED, "child_closed");
            }
        }
    }
}
