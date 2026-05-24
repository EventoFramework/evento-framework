package com.evento.server.bus.correlation;

import com.evento.server.bus.NodeAddress;
import com.evento.transport.message.Response;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One in-flight request awaiting a {@link Response}.
 *
 * @param correlationId  matches the wire {@code correlationId}
 * @param from           originator of the request (may be null for server-initiated)
 * @param to             routed destination
 * @param payloadType    business payload type, useful for metrics and logs
 * @param createdAt      epoch millis at submit time
 * @param timeoutMs      absolute timeout window from {@code createdAt}; 0 = never
 * @param future         completed with the {@link Response} (or exceptionally)
 */
public record PendingCorrelation(
        UUID correlationId,
        NodeAddress from,
        NodeAddress to,
        String payloadType,
        long createdAt,
        long timeoutMs,
        CompletableFuture<Response> future
) {

    public boolean isExpired(long nowMs) {
        return timeoutMs > 0 && nowMs - createdAt > timeoutMs;
    }

    public Instant createdInstant() {
        return Instant.ofEpochMilli(createdAt);
    }
}
