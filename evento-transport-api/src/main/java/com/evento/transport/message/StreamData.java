package com.evento.transport.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * One frame of a streamed reply (2.6), handler → caller, relayed by the broker as is.
 *
 * <p>A request asks for a streamed reply with {@link Request#streamWindow()}: the handler
 * may send that many frames, and then one more for each credit the caller grants with
 * {@link StreamCredit}. Frames of one stream carry consecutive {@code seq} numbers from 0
 * and are delivered in order (every hop dispatches a stream's frames on one serial lane).
 * The stream ends with the frame that has {@code last} set, or with one that carries a
 * {@code failure} (a transport-level failure: the handler could not stream; a failure of
 * the query itself travels in the payload, like any reply). The caller may stop it
 * earlier with {@link StreamCancel}.
 *
 * <p>Only sent to peers that negotiated {@link com.evento.transport.HandshakeProtocol#CAPABILITY_STREAM}:
 * an older peer cannot decode it.
 */
public record StreamData(
        UUID correlationId,
        long seq,
        byte[] payload,
        boolean last,
        ResponseError failure,
        long timestampMs
) implements Message {

    @JsonCreator
    public StreamData(
            @JsonProperty("correlationId") UUID correlationId,
            @JsonProperty("seq") long seq,
            @JsonProperty("payload") byte[] payload,
            @JsonProperty("last") boolean last,
            @JsonProperty("failure") ResponseError failure,
            @JsonProperty("timestampMs") long timestampMs
    ) {
        this.correlationId = correlationId;
        this.seq = seq;
        this.payload = payload == null ? new byte[0] : payload;
        this.last = last || failure != null;
        this.failure = failure;
        this.timestampMs = timestampMs;
    }

    /** A frame of data; {@code last} on the final one. */
    public static StreamData data(UUID correlationId, long seq, byte[] payload, boolean last) {
        return new StreamData(correlationId, seq, payload, last, null, System.currentTimeMillis());
    }

    /** The frame that ends a stream in failure. */
    public static StreamData failure(UUID correlationId, long seq, ResponseError failure) {
        return new StreamData(correlationId, seq, null, true, failure, System.currentTimeMillis());
    }

    @JsonIgnore
    public boolean isFailure() {
        return failure != null;
    }
}
