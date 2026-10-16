package com.evento.common.modeling.messaging.query;

import com.evento.common.modeling.messaging.payload.View;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One page of a larger result: the views of this page plus what the caller needs to ask
 * for the next one.
 *
 * <p>Two styles, the same type:
 * <ul>
 *   <li><b>Offset</b> ({@link #of(Collection, int, int, long)}): page {@code number} of
 *       {@code size} views, with the total when the projection knows it cheaply
 *       ({@code -1} when it does not).</li>
 *   <li><b>Cursor</b> ({@link #ofCursor(Collection, String)}): an opaque token the
 *       projection reads back to continue after the last view of this page (a keyset,
 *       "the last id seen"), {@code null} on the last page. Stable under concurrent
 *       writes, and it does not cost the database a {@code count(*)} or an {@code OFFSET}
 *       scan, so prefer it for large or live data.</li>
 * </ul>
 * The query that asks for a page carries a {@link PageRequest}.
 *
 * <p>A page is bounded by construction, which is the point: a projection that would
 * otherwise answer with an unbounded {@link Multiple} answers with a page, and the
 * caller pulls the rest page by page. To consume a large result as one sequence without
 * paging by hand, stream the query instead ({@code QueryGateway#stream}).
 *
 * @param <T> the view type
 */
public class Page<T extends View> extends QueryResponse<T> {

    private List<T> data = List.of();
    private int number;
    private int size;
    private long totalElements = -1;
    private String nextCursor;
    private boolean cursorPaged;

    /** For the codec. */
    public Page() {
    }

    /**
     * An offset page.
     *
     * @param items         the views of this page
     * @param number        the page index, from 0
     * @param size          the page size that was asked for
     * @param totalElements the total number of views, or {@code -1} when unknown
     */
    public static <R extends View> Page<R> of(Collection<R> items, int number, int size, long totalElements) {
        var page = new Page<R>();
        page.setData(new ArrayList<>(items));
        page.setNumber(number);
        page.setSize(size);
        page.setTotalElements(totalElements);
        return page;
    }

    /**
     * A cursor page.
     *
     * @param items      the views of this page
     * @param nextCursor the token that continues after this page, {@code null} on the last one
     */
    public static <R extends View> Page<R> ofCursor(Collection<R> items, String nextCursor) {
        var page = new Page<R>();
        page.setData(new ArrayList<>(items));
        page.setSize(page.getData().size());
        page.setNextCursor(nextCursor);
        page.setCursorPaged(true);
        return page;
    }

    /** The views of this page. */
    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data == null ? List.of() : data;
    }

    /** The page index from 0 (offset pages; 0 for cursor pages). */
    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    /** The page size that was asked for. */
    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    /** The total number of views, or {@code -1} when the projection does not know it. */
    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    /** The token that continues after this page; {@code null} for offset pages and on the last page. */
    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    /** Whether this page continues by cursor ({@link #ofCursor}) rather than by number. */
    public boolean isCursorPaged() {
        return cursorPaged;
    }

    public void setCursorPaged(boolean cursorPaged) {
        this.cursorPaged = cursorPaged;
    }

    /**
     * Whether there is a page after this one. A cursor page: it has a next cursor. An
     * offset page: the known total goes beyond it, or, with no total, it is full (the
     * next one may then be empty).
     */
    @JsonIgnore
    public boolean hasNext() {
        if (cursorPaged) return nextCursor != null;
        if (totalElements >= 0) return (long) (number + 1) * size < totalElements;
        return size > 0 && data.size() >= size;
    }

    /** The request for the page after this one, or {@code null} when this is the last. */
    @JsonIgnore
    public PageRequest next() {
        if (!hasNext()) return null;
        return cursorPaged ? PageRequest.after(nextCursor, size) : PageRequest.of(number + 1, size);
    }

    /** Total pages for an offset page with a known total, else {@code -1}. */
    @JsonIgnore
    public long getTotalPages() {
        if (cursorPaged || totalElements < 0 || size <= 0) return -1;
        return (totalElements + size - 1) / size;
    }

    @Override
    public String toString() {
        return "Page{number=" + number + ", size=" + size + ", items=" + data.size()
                + ", totalElements=" + totalElements + ", nextCursor=" + nextCursor + '}';
    }
}
