package com.evento.transport.netty;

import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.Codec;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import com.evento.transport.reconnect.ReconnectStrategy;
import io.netty.handler.ssl.SslContext;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Tunable parameters for a Netty transport. Defaults are sensible for production:
 *
 * <ul>
 *   <li>Heartbeat: write idle = 15s (Ping cadence), read idle = 45s (close threshold)</li>
 *   <li>Backpressure: high = 64KB, low = 32KB</li>
 *   <li>Max frame = 16MB</li>
 *   <li>Business handlers run on virtual threads</li>
 *   <li>TLS off (set {@code sslContext} to enable)</li>
 * </ul>
 *
 * <p>{@code sslContext} is optional. When set, the pipeline prepends an
 * {@code SslHandler} so that the wire is encrypted end-to-end. The same
 * record is used for both client and server transports — pass an
 * {@code SslContext} built with {@code forClient()} or {@code forServer()}
 * accordingly.
 */
public record NettyTransportConfig(
        Duration heartbeatWriteIdle,
        Duration heartbeatReadIdle,
        Duration connectTimeout,
        int maxFrameLength,
        int writeBufferHighWaterMark,
        int writeBufferLowWaterMark,
        ReconnectStrategy reconnectStrategy,
        Codec codec,
        Executor businessExecutor,
        SslContext sslContext
) {

    public NettyTransportConfig {
        Objects.requireNonNull(heartbeatWriteIdle, "heartbeatWriteIdle");
        Objects.requireNonNull(heartbeatReadIdle, "heartbeatReadIdle");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(reconnectStrategy, "reconnectStrategy");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(businessExecutor, "businessExecutor");
        if (writeBufferLowWaterMark >= writeBufferHighWaterMark) {
            throw new IllegalArgumentException("low water mark must be < high water mark");
        }
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException("maxFrameLength must be > 0");
        }
    }

    /** Plaintext-friendly constructor; convenient for the common case + every existing call site. */
    public NettyTransportConfig(
            Duration heartbeatWriteIdle,
            Duration heartbeatReadIdle,
            Duration connectTimeout,
            int maxFrameLength,
            int writeBufferHighWaterMark,
            int writeBufferLowWaterMark,
            ReconnectStrategy reconnectStrategy,
            Codec codec,
            Executor businessExecutor
    ) {
        this(heartbeatWriteIdle, heartbeatReadIdle, connectTimeout, maxFrameLength,
                writeBufferHighWaterMark, writeBufferLowWaterMark,
                reconnectStrategy, codec, businessExecutor, null);
    }

    public NettyTransportConfig withSslContext(SslContext ssl) {
        return new NettyTransportConfig(heartbeatWriteIdle, heartbeatReadIdle, connectTimeout,
                maxFrameLength, writeBufferHighWaterMark, writeBufferLowWaterMark,
                reconnectStrategy, codec, businessExecutor, ssl);
    }

    public static NettyTransportConfig defaults() {
        return new NettyTransportConfig(
                Duration.ofSeconds(15),
                Duration.ofSeconds(45),
                Duration.ofSeconds(5),
                HandshakeProtocol.MAX_FRAME_LENGTH,
                64 * 1024,
                32 * 1024,
                new ExponentialBackoffWithJitter(),
                new JacksonCborCodec(),
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }
}
