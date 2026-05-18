package com.evento.transport.netty;

import com.evento.transport.HandshakeProtocol;
import com.evento.transport.codec.Codec;
import com.evento.transport.codec.JacksonCborCodec;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import com.evento.transport.reconnect.ReconnectStrategy;

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
 * </ul>
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
        Executor businessExecutor
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
