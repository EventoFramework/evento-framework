package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;
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
 *
 * <p><b>Additive fields (2.6).</b> All three travel end to end: the broker forwards the
 * bytes it received, so a broker that predates them still delivers them, and a
 * handler that predates them ignores them (and answers the plain way, which every
 * caller accepts).
 * <ul>
 *   <li>{@code acceptEncodings}: payload encodings the caller can decode in the reply
 *       (see {@code PayloadEncoding}); empty = identity only.</li>
 *   <li>{@code streamWindow}: {@code > 0} asks for the reply as a stream of
 *       {@link StreamData} frames, of which the handler may send this many before the
 *       caller grants more with {@link StreamCredit}; {@code 0} = one {@link Response}.</li>
 *   <li>{@code streamDirect}: the caller can also take the stream on a direct connection
 *       to the handler, if the handler offers one ({@link StreamRedirect}).</li>
 * </ul>
 */
public record Request(
        UUID correlationId,
        String sourceBundleId,
        String sourceInstanceId,
        String sourceBundleVersion,
        String payloadType,
        byte[] payload,
        long timeoutMillis,
        long timestampMs,
        // Left off the wire when unset, so a plain request costs what it did before.
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Set<String> acceptEncodings,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) int streamWindow,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean streamDirect
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
            @JsonProperty("timestampMs") long timestampMs,
            @JsonProperty("acceptEncodings") Set<String> acceptEncodings,
            @JsonProperty("streamWindow") int streamWindow,
            @JsonProperty("streamDirect") boolean streamDirect
    ) {
        this.correlationId = correlationId;
        this.sourceBundleId = sourceBundleId;
        this.sourceInstanceId = sourceInstanceId;
        this.sourceBundleVersion = sourceBundleVersion;
        this.payloadType = payloadType;
        this.payload = payload == null ? new byte[0] : payload;
        this.timeoutMillis = timeoutMillis;
        this.timestampMs = timestampMs;
        this.acceptEncodings = acceptEncodings == null ? Set.of() : Set.copyOf(acceptEncodings);
        this.streamWindow = Math.max(0, streamWindow);
        this.streamDirect = streamDirect;
    }

    /** Pre-2.6 shape: identity payloads, a single {@link Response}. */
    public Request(UUID correlationId, String sourceBundleId, String sourceInstanceId,
                   String sourceBundleVersion, String payloadType, byte[] payload,
                   long timeoutMillis, long timestampMs) {
        this(correlationId, sourceBundleId, sourceInstanceId, sourceBundleVersion, payloadType,
                payload, timeoutMillis, timestampMs, null, 0, false);
    }

    /** Whether the caller asked for the reply as a stream. */
    public boolean isStreamed() {
        return streamWindow > 0;
    }

    /** Whether the caller can decode a reply in {@code encoding}. */
    public boolean accepts(String encoding) {
        return encoding != null && acceptEncodings.contains(encoding);
    }

    /** The same request with another remaining budget, sent now (a resend). */
    public Request withTimeout(long remainingMillis, long nowMs) {
        return new Request(correlationId, sourceBundleId, sourceInstanceId, sourceBundleVersion, payloadType,
                payload, remainingMillis, nowMs, acceptEncodings, streamWindow, streamDirect);
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
