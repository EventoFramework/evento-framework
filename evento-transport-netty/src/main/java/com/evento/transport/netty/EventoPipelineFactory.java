package com.evento.transport.netty;

import com.evento.transport.Frame;
import com.evento.transport.state.ConnectionStateMachine;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Builds the standard Evento channel pipeline for one connection. Used by both
 * the client transport (single channel) and the server transport (per-accept).
 *
 * <p>Pipeline order matters: the length-frame decoder must come first so that the
 * CBOR decoder receives one complete frame per invocation.
 */
final class EventoPipelineFactory {

    private final NettyTransportConfig config;
    private final String peerHost;
    private final int peerPort;

    /** Server-side factory: TLS handlers are built without a peer identity. */
    EventoPipelineFactory(NettyTransportConfig config) {
        this(config, null, -1);
    }

    /**
     * Client-side factory. When {@code peerHost} is non-null the TLS handler is
     * created with the remote host/port so that SNI is sent and the peer
     * certificate is verified against that host. Netty 4.2 enables endpoint
     * identification by default, so a handler built without a peer identity
     * fails the handshake — the host/port must be threaded through here.
     */
    EventoPipelineFactory(NettyTransportConfig config, String peerHost, int peerPort) {
        this.config = config;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
    }

    void configure(Channel ch,
                   ConnectionStateMachine state,
                   AtomicLong lastInboundMs,
                   Consumer<Frame> frameListener) {
        ChannelPipeline p = ch.pipeline();
        // TLS, if configured, must run before any framing/decoder so it operates on raw socket bytes.
        if (config.sslContext() != null) {
            var ssl = peerHost != null
                    ? config.sslContext().newHandler(ch.alloc(), peerHost, peerPort)
                    : config.sslContext().newHandler(ch.alloc());
            p.addLast("tls", ssl);
        }
        p.addLast("frameDec", new LengthFieldBasedFrameDecoder(
                config.maxFrameLength(), 0, 4, 0, 4));
        p.addLast("frameEnc", new LengthFieldPrepender(4));
        p.addLast("chunkReassembler", new ChunkReassembler());
        p.addLast("chunkEncoder", new ChunkingEncoder(config.maxFrameLength()));
        p.addLast("cborDec", new CborMessageDecoder(config.codec()));
        p.addLast("cborEnc", new CborMessageEncoder(config.codec()));
        p.addLast("idle", new IdleStateHandler(
                config.heartbeatReadIdle().toMillis(),
                config.heartbeatWriteIdle().toMillis(),
                0L,
                TimeUnit.MILLISECONDS));
        p.addLast("heartbeat", new HeartbeatHandler());
        p.addLast("backpressure", new BackpressureHandler(state));
        p.addLast("inbound", new MessageInboundHandler(
                frameListener, config.businessExecutor(), lastInboundMs, state));
    }
}
