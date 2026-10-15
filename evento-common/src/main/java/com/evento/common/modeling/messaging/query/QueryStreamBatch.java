package com.evento.common.modeling.messaging.query;

import com.evento.common.modeling.messaging.payload.View;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * One frame of a streamed query answer: a batch of views, in order. Framework-internal
 * (the body of a stream frame); applications see the views through {@code QueryStream}.
 */
public class QueryStreamBatch implements Serializable {

    private ArrayList<View> items;

    /** For the codec. */
    public QueryStreamBatch() {
        this.items = new ArrayList<>();
    }

    public QueryStreamBatch(List<? extends View> items) {
        this.items = new ArrayList<>(items);
    }

    public List<View> getItems() {
        return items == null ? List.of() : items;
    }

    /**
     * The views of any query answer, lazily for a {@link Streamed} one: what a streamed
     * query sends, batch by batch. Close the stream (it closes a {@link Streamed} source).
     *
     * @throws IllegalArgumentException for an answer type that holds no views
     */
    public static Stream<? extends View> views(QueryResponse<?> response) {
        return switch (response) {
            case null -> Stream.empty();
            case Single<?> single -> Stream.ofNullable(single.getData());
            case Multiple<?> multiple -> multiple.getData() == null ? Stream.empty() : multiple.getData().stream();
            case Page<?> page -> page.getData().stream();
            case Streamed<?> streamed -> streamed.open();
            default -> throw new IllegalArgumentException("cannot stream a " + response.getClass().getName()
                    + ": answer with Single, Multiple, Page or Streamed");
        };
    }
}
