package com.evento.common.modeling.messaging.query;

import com.evento.common.modeling.messaging.payload.View;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A query result produced lazily, view by view, so that neither the projection, nor the
 * broker, nor the caller ever needs the whole result in memory.
 *
 * <p>The projection returns it with a <em>source</em> it has not opened yet:
 *
 * <pre>{@code
 * @QueryHandler
 * Streamed<OrderView> handle(ExportOrders q) {
 *     return Streamed.of(() -> repository.streamByCustomer(q.getCustomerId()));  // a cursor
 * }
 * }</pre>
 *
 * <ul>
 *   <li>Asked with {@code QueryGateway#stream}, the framework opens the source, sends the
 *       views in frames as fast as the caller consumes them (flow control end to end),
 *       and closes the source at the end, on cancel, or on failure.</li>
 *   <li>Asked with the plain {@code QueryGateway#query}, the views are collected into
 *       {@link #getData()} and sent in one reply, as a {@link Multiple} would be: the
 *       query keeps working for callers that do not stream, at a {@code Multiple}'s cost.</li>
 * </ul>
 *
 * <p>The source is opened <em>after</em> the query handler has returned, outside any
 * {@code MessageHandlerInterceptor} around it: a source that needs a transaction or a
 * connection (a JPA or JDBC stream) must open and close it itself, inside the supplier
 * and the stream's {@code onClose}.
 *
 * @param <T> the view type
 */
public class Streamed<T extends View> extends QueryResponse<T> {

    private List<T> data;

    @JsonIgnore
    private transient Supplier<? extends Stream<T>> source;

    /** For the codec. */
    public Streamed() {
    }

    /** Views read from {@code source}, opened only when the result is consumed. */
    public static <R extends View> Streamed<R> of(Supplier<? extends Stream<R>> source) {
        Objects.requireNonNull(source, "source");
        var streamed = new Streamed<R>();
        streamed.source = source;
        return streamed;
    }

    /** Views already at hand (still sent frame by frame to a streaming caller). */
    public static <R extends View> Streamed<R> of(Iterable<R> views) {
        Objects.requireNonNull(views, "views");
        return of(() -> StreamSupport.stream(views.spliterator(), false));
    }

    /**
     * Opens the views as a stream the caller must close. A result received from another
     * bundle (collected) streams its data.
     */
    @JsonIgnore
    public Stream<T> open() {
        if (data == null && source != null) {
            var opened = source.get();
            return opened == null ? Stream.empty() : opened;
        }
        return data == null ? Stream.empty() : data.stream();
    }

    /** Whether the views are still to be produced (not yet collected). */
    @JsonIgnore
    public boolean isLazy() {
        return source != null && data == null;
    }

    /**
     * All the views. On a lazy result this reads the whole source (and closes it) and
     * keeps the views: this is what a plain, non-streaming query costs.
     */
    public List<T> getData() {
        materialize();
        return data == null ? List.of() : data;
    }

    /**
     * Reads a lazy source to the end (closing it) and keeps the views, so the result can
     * be sent in one reply. The framework calls it before a plain reply; a no-op once done.
     *
     * @return this
     */
    public Streamed<T> materialize() {
        if (data == null && source != null) {
            try (var views = open()) {
                data = views.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            }
        }
        return this;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return isLazy() ? "Streamed{lazy}" : "Streamed{items=" + (data == null ? 0 : data.size()) + '}';
    }
}
