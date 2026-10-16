package com.evento.application.client.stream;

import com.evento.transport.RequestTimeoutException;
import com.evento.transport.codec.PayloadEncoding;
import com.evento.transport.message.Message;
import com.evento.transport.message.Response;
import com.evento.transport.message.ResponseError;
import com.evento.transport.message.StreamCancel;
import com.evento.transport.message.StreamCredit;
import com.evento.transport.message.StreamData;
import com.evento.transport.message.StreamRedirect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The caller's end of a streamed reply: frames in arrival order, credit granted back as
 * they are consumed.
 *
 * <p>The handler may send {@code window} frames ahead; this side returns credit in
 * batches of half the window as the application takes frames with {@link #next}, so the
 * pipe stays full while no more than a window of frames is ever in flight or buffered
 * here. A handler that cannot stream (an older bundle, or a session without the stream
 * capability) answers with a plain reply, which arrives as one {@link Item.Whole}: callers
 * treat both the same way.
 *
 * <p>{@link #next} waits at most the idle timeout for each frame; the stream then fails
 * with {@link RequestTimeoutException} and is cancelled. Closing a stream that has not
 * ended cancels it, so the handler stops producing and closes its source.
 *
 * <p><b>Direct streams.</b> When the handler offers the stream on a direct connection
 * ({@link StreamRedirect}), {@link #next} hands the redirect to the
 * {@link #onRedirect redirect handler}, which connects, {@linkplain #switchControl switches}
 * credit and cancel to that connection and feeds the frames it reads to
 * {@link #accept(StreamData)}; or, when it cannot connect, asks again through the broker
 * under a new correlation id ({@link #retarget}). The application sees one stream either way.
 */
public final class InboundStream implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InboundStream.class);

    /** What {@link #next} returns. */
    public sealed interface Item permits Item.Frame, Item.Whole, Item.End {
        /** One frame of the stream. */
        record Frame(byte[] payload) implements Item {}
        /** The whole answer in one reply: the handler did not stream. */
        record Whole(byte[] payload) implements Item {}
        /** The stream ended normally. */
        record End() implements Item {}
    }

    /** The stream ended in failure; carries the failure as it arrived on the wire. */
    public static final class StreamFailedException extends RuntimeException {
        private final transient ResponseError error;

        public StreamFailedException(ResponseError error) {
            super(error == null ? "stream failed" : error.message());
            this.error = error;
        }

        public ResponseError error() {
            return error;
        }
    }

    private sealed interface Signal permits Data, Done, Failed, Redirected {}
    private record Data(Item item) implements Signal {}
    private record Done() implements Signal {}
    private record Failed(ResponseError error) implements Signal {}
    private record Redirected(StreamRedirect redirect) implements Signal {}

    private volatile UUID correlationId;
    private final String payloadType;
    private final int window;
    private final long idleTimeoutNanos;
    private volatile Consumer<Message> control;
    private final Consumer<UUID> unregister;
    private final List<Runnable> finishActions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean released = new AtomicBoolean();
    private final LinkedBlockingQueue<Signal> signals = new LinkedBlockingQueue<>();
    private volatile Consumer<StreamRedirect> redirectHandler;
    private long expectedSeq;
    private int consumedSinceGrant;
    private volatile boolean finished;
    private volatile boolean closed;
    private long frames;

    /**
     * @param window      frames the handler may send ahead of consumption
     * @param idleTimeout how long {@link #next} waits for a frame
     * @param control     sends credit and cancel frames back to the handler
     * @param unregister  forgets the stream under its (current) correlation id
     */
    public InboundStream(UUID correlationId, String payloadType, int window, Duration idleTimeout,
                         Consumer<Message> control, Consumer<UUID> unregister) {
        this.correlationId = correlationId;
        this.payloadType = payloadType;
        this.window = Math.max(1, window);
        this.idleTimeoutNanos = idleTimeout == null || idleTimeout.isZero() || idleTimeout.isNegative()
                ? TimeUnit.SECONDS.toNanos(30) : idleTimeout.toNanos();
        this.control = control;
        this.unregister = unregister == null ? id -> { } : unregister;
    }

    public UUID correlationId() {
        return correlationId;
    }

    public String payloadType() {
        return payloadType;
    }

    public int window() {
        return window;
    }

    /** Frames delivered to the application so far. */
    public long frames() {
        return frames;
    }

    /** Whether the stream has ended (normally, in failure, or closed). */
    public boolean isFinished() {
        return finished;
    }

    /** Where a {@link StreamRedirect} goes (the direct-connection path); null refuses it. */
    public void onRedirect(Consumer<StreamRedirect> handler) {
        this.redirectHandler = handler;
    }

    /** Credit and cancel go to {@code newControl} from now on (a direct connection). */
    public void switchControl(Consumer<Message> newControl) {
        this.control = newControl;
    }

    /**
     * The stream continues under {@code newCorrelationId}, through {@code newControl}: a
     * redirect that could not be followed is asked again through the broker. Only before
     * the first frame (a redirect comes before any).
     */
    public void retarget(UUID newCorrelationId, Consumer<Message> newControl) {
        unregister.accept(correlationId);
        this.correlationId = newCorrelationId;
        this.control = newControl;
        this.expectedSeq = 0;
    }

    /** Runs {@code action} once, when the stream ends or is closed (releases a direct connection). */
    public void onFinish(Runnable action) {
        finishActions.add(action);
        if (released.get()) action.run();
    }

    // ---- inbound, from the dispatcher or a direct connection ----

    /** A frame arrived (in order: stream frames are dispatched on one lane per connection). */
    public void accept(StreamData data) {
        if (finished) return;
        if (data.seq() != expectedSeq) {
            signals.add(new Failed(new ResponseError(IllegalStateException.class.getName(),
                    "stream " + correlationId + " frame " + data.seq() + " arrived, expected " + expectedSeq, null)));
            return;
        }
        expectedSeq++;
        if (data.isFailure()) {
            signals.add(new Failed(data.failure()));
            return;
        }
        if (data.payload().length > 0) {
            try {
                signals.add(new Data(new Item.Frame(PayloadEncoding.decode(data.payload()))));
            } catch (RuntimeException corrupt) {
                signals.add(new Failed(ResponseError.of(corrupt)));
                return;
            }
        }
        if (data.last()) signals.add(new Done());
    }

    /** A plain reply arrived: the whole answer, or a failure the broker or handler sent instead. */
    public void accept(Response response) {
        if (finished) return;
        if (response.isError()) {
            signals.add(new Failed(response.error()));
            return;
        }
        try {
            signals.add(new Data(new Item.Whole(PayloadEncoding.decode(response.payload()))));
            signals.add(new Done());
        } catch (RuntimeException corrupt) {
            signals.add(new Failed(ResponseError.of(corrupt)));
        }
    }

    /** The handler offers the stream on a direct connection. */
    public void accept(StreamRedirect redirect) {
        if (finished) return;
        signals.add(new Redirected(redirect));
    }

    /** The stream cannot go on (the session ended, the handler cancelled it). */
    public void fail(Throwable cause) {
        if (finished) return;
        signals.add(new Failed(ResponseError.of(cause)));
    }

    // ---- outbound, for the application ----

    /**
     * The next item: a {@link Item.Frame}, a {@link Item.Whole} answer, or {@link Item.End}.
     *
     * @throws StreamFailedException when the stream failed or timed out (it is then closed)
     */
    public Item next() throws InterruptedException {
        if (finished) return new Item.End();
        while (true) {
            var signal = signals.poll(idleTimeoutNanos, TimeUnit.NANOSECONDS);
            if (signal == null) {
                var error = new ResponseError(RequestTimeoutException.class.getName(),
                        "stream " + payloadType + " received nothing for "
                                + TimeUnit.NANOSECONDS.toMillis(idleTimeoutNanos) + " ms", null);
                close();
                throw new StreamFailedException(error);
            }
            switch (signal) {
                case Data(Item.Frame frame) -> {
                    frames++;
                    grantFor(1);
                    return frame;
                }
                case Data(Item item) -> {
                    return item;
                }
                case Done() -> {
                    finished = true;
                    release();
                    return new Item.End();
                }
                case Failed(ResponseError error) -> {
                    finished = true;
                    release();
                    throw new StreamFailedException(error);
                }
                case Redirected(StreamRedirect redirect) -> {
                    var handler = redirectHandler;
                    if (handler == null) {
                        finished = true;
                        release();
                        throw new StreamFailedException(new ResponseError(IllegalStateException.class.getName(),
                                "stream " + correlationId + " redirected, but this caller did not ask for it", null));
                    }
                    // Connects (or falls back to the broker) and feeds this same stream.
                    handler.accept(redirect);
                }
            }
        }
    }

    private void grantFor(int consumed) {
        consumedSinceGrant += consumed;
        if (consumedSinceGrant >= Math.max(1, window / 2)) {
            int grant = consumedSinceGrant;
            consumedSinceGrant = 0;
            try {
                control.accept(StreamCredit.of(correlationId, grant));
            } catch (RuntimeException e) {
                log.debug("event=stream_credit_not_sent correlationId={} cause={}", correlationId, e.toString());
            }
        }
    }

    /** Closes the stream; one that has not ended is cancelled at the handler. Idempotent. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (!finished) {
            finished = true;
            try {
                control.accept(StreamCancel.of(correlationId, "closed by the caller"));
            } catch (RuntimeException e) {
                log.debug("event=stream_cancel_not_sent correlationId={} cause={}", correlationId, e.toString());
            }
        }
        release();
    }

    private void release() {
        if (!released.compareAndSet(false, true)) return;
        unregister.accept(correlationId);
        for (var action : finishActions) {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.debug("event=stream_finish_action_failed correlationId={} cause={}", correlationId, e.toString());
            }
        }
    }
}
