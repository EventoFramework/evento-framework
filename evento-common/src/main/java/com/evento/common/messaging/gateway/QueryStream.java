package com.evento.common.messaging.gateway;

import com.evento.common.modeling.messaging.payload.View;
import com.evento.common.modeling.messaging.query.QueryResponse;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The views of a streamed query, pulled one by one: the next batch travels only when the
 * consumer has made room for it, so memory stays bounded whatever the result's size.
 *
 * <p>Iterate it once, and close it — a stream abandoned before its end keeps the
 * answering bundle's source open until it times out. Try-with-resources does both:
 *
 * <pre>{@code
 * try (var orders = queryGateway.stream(new ExportOrders(customerId))) {
 *     orders.forEach(csv::write);
 * }
 * }</pre>
 *
 * <p>A failure (the answering bundle failed, the stream timed out, the connection was
 * lost) surfaces from {@code hasNext()}/{@code next()} as a {@link QueryStreamException}.
 *
 * @param <V> the view type
 */
public interface QueryStream<V extends View> extends Iterable<V>, AutoCloseable {

    /** Stops the stream: no more views are produced or sent, the source is closed. Idempotent. */
    @Override
    void close();

    /** The views as a sequential {@link Stream}; closing it closes this. */
    default Stream<V> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED), false)
                .onClose(this::close);
    }

    /** Raised from iteration when the stream ends in failure. */
    class QueryStreamException extends RuntimeException {
        public QueryStreamException(String message) { super(message); }
        public QueryStreamException(String message, Throwable cause) { super(message, cause); }
    }

    /** A stream over views at hand, closing {@code onClose} when closed. */
    static <V extends View> QueryStream<V> of(Iterator<V> views, Runnable onClose) {
        Objects.requireNonNull(views, "views");
        return new QueryStream<>() {
            private boolean closed;

            @Override
            public Iterator<V> iterator() {
                return views;
            }

            @Override
            public synchronized void close() {
                if (closed) return;
                closed = true;
                if (onClose != null) onClose.run();
            }
        };
    }

    /**
     * The views of a whole answer: a {@code Single}'s view (none when empty), a
     * {@code Multiple}'s, a {@code Page}'s, a {@code Streamed}'s. Used when an answer
     * arrives in one reply rather than streamed.
     */
    @SuppressWarnings("unchecked")
    static <V extends View> QueryStream<V> ofResponse(QueryResponse<?> response) {
        Stream<V> views;
        try {
            views = (Stream<V>) com.evento.common.modeling.messaging.query.QueryStreamBatch.views(response);
        } catch (IllegalArgumentException e) {
            throw new QueryStreamException(e.getMessage(), e);
        }
        return of(views.iterator(), views::close);
    }

    /** A stream with no views. */
    static <V extends View> QueryStream<V> empty() {
        return of(List.<V>of().iterator(), null);
    }

    /** A stream that fails as soon as it is read. */
    static <V extends View> QueryStream<V> failed(Throwable cause) {
        var error = cause instanceof QueryStreamException qse ? qse
                : new QueryStreamException(cause == null ? "stream failed" : String.valueOf(cause.getMessage()), cause);
        return of(new Iterator<>() {
            @Override public boolean hasNext() { throw error; }
            @Override public V next() { throw error; }
        }, null);
    }
}
