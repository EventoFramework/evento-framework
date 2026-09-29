package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * RPC request. The transport layer is payload-agnostic: {@code payload} is opaque
 * (typically CBOR-encoded user-domain object) and routing is decided by
 * {@code payloadType} (a stable type name agreed by client/server registries).
 *
 * <p><b>Deadline.</b> {@code timeoutMillis} is the sender's remaining budget when it
 * sent the request: how long it will wait for the reply ({@code 0} = no deadline).
 * Every sender fills it from its request timeout, so it is the caller's deadline
 * carried relative to the send, which needs no clock agreement between machines. A
 * receiver turns it into a local deadline with {@link #deadlineFrom} and the time the
 * request reached it; the broker drops requests whose deadline passed before it
 * forwards, locks or appends anything. Measured from the arrival, the deadline
 * errs late by the one-way network delay, never early. {@code timestampMs} is the
 * sender's clock and is not used for this.
 */
public record Request(
        UUID correlationId,
        String sourceBundleId,
        String sourceInstanceId,
        String sourceBundleVersion,
        String payloadType,
        byte[] payload,
        long timeoutMillis,
        long timestampMs
) implements Message {

    @JsonCreator
    public Request(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("sourceBundleId") String sourceBundleId,
            @JsonProperty("sourceInstanceId") String sourceInstanceId,
            @JsonProperty("sourceBundleVersion") String sourceBundleVersion,
            @JsonProperty("payloadType") String payloadType,
            @JsonProperty("payload") byte[] payload,
            @JsonProperty("timeoutMillis") long timeoutMillis,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.sourceBundleId = sourceBundleId;
        this.sourceInstanceId = sourceInstanceId;
        this.sourceBundleVersion = sourceBundleVersion;
        this.payloadType = payloadType;
        this.payload = payload == null ? new byte[0] : payload;
        this.timeoutMillis = timeoutMillis;
        this.timestampMs = timestampMs;
    }

    /**
     * Whether the request is older than its timeout by the <em>sender's</em> clock
     * ({@code timestampMs}). Only meaningful on the sending side; a receiver should use
     * {@link #deadlineFrom}, which does not compare clocks of different machines.
     */
    public boolean isExpired(long nowMs) {
        return timeoutMillis > 0 && nowMs - timestampMs > timeoutMillis;
    }

    /**
     * The local deadline of a request that arrived at {@code receivedAtMs}:
     * {@code receivedAtMs + timeoutMillis}, or {@link Long#MAX_VALUE} when the sender set
     * no timeout (which keeps pre-deadline behaviour: nothing is ever dropped). The result
     * is on the clock {@code receivedAtMs} was read from; the broker uses a monotonic one
     * ({@link com.evento.transport.Frame#receivedAtMonotonicMs()}), so a wall-clock step
     * neither expires nor stretches deadlines.
     */
    public long deadlineFrom(long receivedAtMs) {
        if (timeoutMillis <= 0) return Long.MAX_VALUE;
        long deadline = receivedAtMs + timeoutMillis;
        return deadline < receivedAtMs ? Long.MAX_VALUE : deadline;
    }
}
