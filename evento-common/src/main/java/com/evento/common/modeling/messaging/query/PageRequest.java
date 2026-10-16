package com.evento.common.modeling.messaging.query;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Objects;

/**
 * Which page a query asks for: by number ({@link #of(int, int)}) or after a cursor a
 * previous {@link Page} returned ({@link #after(String, int)}). Embed it in a query that
 * answers with a {@link Page}:
 *
 * <pre>{@code
 * public class FindOrders extends Query<Page<OrderView>> {
 *     private String customerId;
 *     private PageRequest page = PageRequest.first(50);
 * }
 * }</pre>
 *
 * A bean (not a record) so it travels inside any query payload the codec already handles.
 */
public class PageRequest implements Serializable {

    private int number;
    private int size;
    private String cursor;

    /** For the codec. */
    public PageRequest() {
    }

    private PageRequest(int number, int size, String cursor) {
        if (number < 0) throw new IllegalArgumentException("page number must be >= 0: " + number);
        if (size <= 0) throw new IllegalArgumentException("page size must be > 0: " + size);
        this.number = number;
        this.size = size;
        this.cursor = cursor;
    }

    /** The first page of {@code size} views. */
    public static PageRequest first(int size) {
        return new PageRequest(0, size, null);
    }

    /** Page {@code number} (from 0) of {@code size} views. */
    public static PageRequest of(int number, int size) {
        return new PageRequest(number, size, null);
    }

    /** The {@code size} views after {@code cursor}, as returned by {@link Page#getNextCursor()}. */
    public static PageRequest after(String cursor, int size) {
        Objects.requireNonNull(cursor, "cursor");
        return new PageRequest(0, size, cursor);
    }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    /** The cursor to continue after, or {@code null} for an offset request. */
    public String getCursor() { return cursor; }
    public void setCursor(String cursor) { this.cursor = cursor; }

    /** Whether this request continues after a cursor. */
    @JsonIgnore
    public boolean isCursor() {
        return cursor != null;
    }

    /** The index of the first view of an offset request: {@code number * size}. */
    @JsonIgnore
    public long offset() {
        return (long) number * size;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PageRequest that && number == that.number && size == that.size
                && Objects.equals(cursor, that.cursor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, size, cursor);
    }

    @Override
    public String toString() {
        return cursor == null ? "PageRequest{number=" + number + ", size=" + size + '}'
                : "PageRequest{after=" + cursor + ", size=" + size + '}';
    }
}
