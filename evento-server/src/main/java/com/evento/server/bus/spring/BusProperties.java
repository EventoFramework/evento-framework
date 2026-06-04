package com.evento.server.bus.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tunables for the v2 server bus. Bound under {@code evento.server.bus.*}
 * via Spring Boot's configuration-properties scanner.
 *
 * <p>The bus is opt-in: set {@code evento.server.bus.enabled=true} to have
 * {@link BusConfiguration} register the beans and start a Netty-based
 * server on {@link #port()}. Defaults are safe for production but every
 * value is overridable from {@code application.properties}.
 */
@ConfigurationProperties(prefix = "evento.server.bus")
public record BusProperties(
        boolean enabled,
        int port,
        String serverInstanceId,
        Duration correlationCheckInterval,
        Duration shutdownDeadline,
        Duration heartbeatWriteIdle,
        Duration heartbeatReadIdle,
        Duration connectTimeout,
        int maxFrameLength,
        int writeBufferHighWaterMark,
        int writeBufferLowWaterMark,
        int businessExecutorCoreSize,
        int businessExecutorMaxSize,
        int businessExecutorQueueCapacity,
        Duration businessExecutorKeepAlive
) {

    public BusProperties {
        if (serverInstanceId == null || serverInstanceId.isBlank()) {
            serverInstanceId = "server-" + java.util.UUID.randomUUID();
        }
        if (correlationCheckInterval == null) correlationCheckInterval = Duration.ofSeconds(1);
        if (shutdownDeadline == null) shutdownDeadline = Duration.ofSeconds(30);
        if (heartbeatWriteIdle == null) heartbeatWriteIdle = Duration.ofSeconds(15);
        if (heartbeatReadIdle == null) heartbeatReadIdle = Duration.ofSeconds(45);
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (maxFrameLength <= 0) maxFrameLength = 16 * 1024 * 1024;
        if (writeBufferHighWaterMark <= 0) writeBufferHighWaterMark = 64 * 1024;
        if (writeBufferLowWaterMark <= 0) writeBufferLowWaterMark = 32 * 1024;
        if (businessExecutorCoreSize <= 0) {
            businessExecutorCoreSize = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        }
        if (businessExecutorMaxSize <= 0) {
            businessExecutorMaxSize = Math.max(businessExecutorCoreSize,
                    Runtime.getRuntime().availableProcessors() * 8);
        }
        if (businessExecutorMaxSize < businessExecutorCoreSize) {
            businessExecutorMaxSize = businessExecutorCoreSize;
        }
        if (businessExecutorQueueCapacity <= 0) businessExecutorQueueCapacity = 1024;
        if (businessExecutorKeepAlive == null) businessExecutorKeepAlive = Duration.ofSeconds(90);
    }
}
