package com.evento.transport;

import com.evento.transport.message.Message;

/**
 * Inbound message paired with the raw bytes it was decoded from.
 *
 * <p>Holding the original {@code rawBytes} lets the broker forward a request
 * to its destination without re-running the codec: just write the bytes back
 * out (via {@link Transport#sendRaw}) and the destination's pipeline frames
 * + transmits them unchanged. Saves one CBOR encode + one allocation per
 * forwarded message — meaningful on the hot path between caller, broker, and
 * handler.
 *
 * <p>{@code rawBytes} is the CBOR body of one wire frame — it does NOT
 * include the 4-byte length prefix. The downstream {@code LengthFieldPrepender}
 * adds the prefix when {@code sendRaw} writes the bytes.
 *
 * <p>The buffer is not retained beyond the dispatcher call. If a handler needs
 * to hold onto the raw bytes past that point, it should copy them itself.
 *
 * <p>{@code receivedAtNanos} is the {@link System#nanoTime()} at which the frame was
 * decoded, on the I/O thread, before it waited in any dispatch queue. A broker
 * measures a request's deadline from it ({@link com.evento.transport.message.Request#deadlineFrom}),
 * so time spent queued behind a saturated executor counts against the caller's
 * budget, and no clock of another machine is involved. It is a monotonic clock, not
 * the wall clock: a deadline is a purely local interval, and an NTP step of the wall
 * clock must neither expire every queued request at once nor stretch them. Only
 * differences between two {@code nanoTime} readings are meaningful.
 */
public record Frame(Message message, byte[] rawBytes, long receivedAtNanos) {

    public Frame {
        if (message == null) throw new IllegalArgumentException("message");
        if (rawBytes == null) throw new IllegalArgumentException("rawBytes");
    }

    /** A frame received now. */
    public Frame(Message message, byte[] rawBytes) {
        this(message, rawBytes, System.nanoTime());
    }

    /** {@link #receivedAtNanos()} in milliseconds, on the same monotonic clock. */
    public long receivedAtMonotonicMs() {
        return receivedAtNanos / 1_000_000L;
    }
}
