package com.evento.application.client.stream;

import com.evento.application.client.handler.HandlerRegistry;
import com.evento.transport.codec.PayloadEncoding;
import com.evento.transport.message.Message;
import com.evento.transport.message.ResponseError;
import com.evento.transport.message.StreamData;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * The handler's end of a streamed reply: frames go out only against credit.
 *
 * <p>The caller's request opens the window ({@code Request.streamWindow}); every frame
 * spends one credit and {@link #grant} adds the credit the caller sends back as it
 * consumes. A handler that has spent its credit blocks in {@link #send} — it produces
 * exactly as fast as the slowest consumer, and no hop accumulates frames. A caller that
 * grants nothing for {@code idleTimeout} is taken as gone: the stream is cancelled, which
 * releases the handler's source instead of parking it forever.
 *
 * <p>The terminal frame (end or failure) spends no credit.
 */
public final class OutboundStream implements HandlerRegistry.StreamSink {

    private final UUID correlationId;
    private final Function<Message, CompletableFuture<Void>> sender;
    private final Semaphore credits;
    private final long idleTimeoutNanos;
    private final int compressionThreshold;
    private final boolean compress;
    private long seq;
    private volatile String cancelled;
    private volatile boolean ended;
    private long framesSent;
    private long bytesSent;

    /**
     * @param initialCredit        the caller's window
     * @param idleTimeout          how long to wait for credit before giving the caller up
     * @param compress             whether the caller accepts compressed frames
     * @param compressionThreshold frames of at least this many bytes are compressed
     */
    public OutboundStream(UUID correlationId, Function<Message, CompletableFuture<Void>> sender, int initialCredit,
                          Duration idleTimeout, boolean compress, int compressionThreshold) {
        this.correlationId = correlationId;
        this.sender = sender;
        this.credits = new Semaphore(Math.max(1, initialCredit));
        this.idleTimeoutNanos = idleTimeout == null || idleTimeout.isZero() || idleTimeout.isNegative()
                ? TimeUnit.SECONDS.toNanos(30) : idleTimeout.toNanos();
        this.compress = compress;
        this.compressionThreshold = compressionThreshold;
    }

    public UUID correlationId() {
        return correlationId;
    }

    @Override
    public void send(byte[] frame) throws HandlerRegistry.StreamCancelledException, InterruptedException {
        checkOpen();
        if (!credits.tryAcquire(idleTimeoutNanos, TimeUnit.NANOSECONDS)) {
            cancel("no credit from the caller for " + TimeUnit.NANOSECONDS.toMillis(idleTimeoutNanos) + " ms");
        }
        checkOpen();
        var payload = compress ? PayloadEncoding.deflateIfWorthwhile(frame, compressionThreshold) : frame;
        try {
            sender.apply(StreamData.data(correlationId, seq++, payload, false));
        } catch (RuntimeException notSent) {
            cancel("frame not sent: " + notSent.getMessage());
            checkOpen();
        }
        framesSent++;
        bytesSent += payload.length;
    }

    @Override
    public boolean isCancelled() {
        return cancelled != null;
    }

    /** Ends the stream normally. */
    public CompletableFuture<Void> complete() {
        if (ended || isCancelled()) return CompletableFuture.completedFuture(null);
        ended = true;
        return terminal(StreamData.data(correlationId, seq++, new byte[0], true));
    }

    /** Ends the stream in failure (the handler could not stream). */
    public CompletableFuture<Void> fail(Throwable cause) {
        if (ended || isCancelled()) return CompletableFuture.completedFuture(null);
        ended = true;
        return terminal(StreamData.failure(correlationId, seq++, ResponseError.of(cause)));
    }

    private CompletableFuture<Void> terminal(StreamData frame) {
        try {
            var written = sender.apply(frame);
            return written == null ? CompletableFuture.completedFuture(null) : written;
        } catch (RuntimeException notSent) {
            return CompletableFuture.failedFuture(notSent);
        }
    }

    /** The caller consumed {@code n} frames: that many more may go. */
    public void grant(int n) {
        // After a cancel the credits no longer matter, and adding to the wake-up permits below
        // could overflow the semaphore.
        if (n > 0 && cancelled == null) credits.release(n);
    }

    /**
     * Stops the stream: the next {@link #send} throws, a send waiting for credit wakes up.
     * Idempotent: only the first call releases the wake-up permits. Each call used to, and a
     * stream cancelled twice (the caller closing while a send gave up on credit) overflowed the
     * semaphore: the handler saw {@code Error: Maximum permit count exceeded} instead of
     * {@link HandlerRegistry.StreamCancelledException}.
     */
    public void cancel(String reason) {
        synchronized (this) {
            if (cancelled != null) return;
            cancelled = reason == null ? "cancelled" : reason;
        }
        credits.release(Integer.MAX_VALUE / 2);
    }

    public long framesSent() {
        return framesSent;
    }

    public long bytesSent() {
        return bytesSent;
    }

    private void checkOpen() throws HandlerRegistry.StreamCancelledException {
        var reason = cancelled;
        if (reason != null) {
            throw new HandlerRegistry.StreamCancelledException("stream " + correlationId + " cancelled: " + reason);
        }
    }
}
