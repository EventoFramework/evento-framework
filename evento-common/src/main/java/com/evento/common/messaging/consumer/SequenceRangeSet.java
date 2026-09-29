package com.evento.common.messaging.consumer;

import java.util.Map;
import java.util.TreeMap;

/**
 * A set of event sequence numbers stored as disjoint, non-adjacent closed intervals.
 *
 * <p>This is what the {@link CheckpointMode#WATERMARK} bookkeeping keeps above the
 * watermark: every number that is known to be resolved — finished by a handler, or
 * proven absent by a fetched batch — waiting for the numbers below it to be resolved
 * too. Holding intervals rather than single numbers is what makes the cost of a gap
 * independent of its width. A consumer that sees one event in seven of a shared
 * sequence, or a Snowflake-numbered store whose consecutive ids are billions apart,
 * closes each gap with one entry instead of one entry per missing number.
 *
 * <p>Size is bounded by the number of <em>unresolved</em> numbers that separate the
 * intervals, i.e. by the handlers still in flight, never by how sparse the stream is.
 *
 * <p>Not thread-safe: the owner synchronises.
 */
final class SequenceRangeSet {

    /** start -> end (both inclusive). Intervals never overlap and never touch. */
    private final TreeMap<Long, Long> ranges = new TreeMap<>();

    /** Mark {@code [from, to]} as resolved. An empty interval ({@code from > to}) is a no-op. */
    void add(long from, long to) {
        if (from > to) return;
        // Merge with a left neighbour that overlaps or ends right before `from`.
        Map.Entry<Long, Long> left = ranges.floorEntry(from);
        if (left != null && left.getValue() >= from - 1) {
            if (left.getValue() >= to) return;          // already covered
            from = left.getKey();
            ranges.remove(from);
        }
        // Absorb every right neighbour that starts inside, or right after, [from, to].
        Map.Entry<Long, Long> right = ranges.ceilingEntry(from);
        while (right != null && right.getKey() - 1 <= to) {
            if (right.getValue() > to) to = right.getValue();
            ranges.remove(right.getKey());
            right = ranges.ceilingEntry(from);
        }
        ranges.put(from, to);
    }

    /**
     * Consume the interval that continues {@code watermark} contiguously, if any.
     *
     * @return the highest number reachable from {@code watermark} through resolved numbers
     *         — {@code watermark} itself when {@code watermark + 1} is not resolved.
     */
    long advance(long watermark) {
        Map.Entry<Long, Long> first = ranges.firstEntry();
        while (first != null && first.getKey() - 1 <= watermark) {
            ranges.pollFirstEntry();
            if (first.getValue() > watermark) watermark = first.getValue();
            first = ranges.firstEntry();
        }
        return watermark;
    }

    /**
     * Forget everything at or below {@code watermark} without moving it. Used when another
     * instance advanced the persisted checkpoint while this one did not hold the lock.
     */
    void discardThrough(long watermark) {
        Map.Entry<Long, Long> first = ranges.firstEntry();
        while (first != null && first.getKey() <= watermark) {
            ranges.pollFirstEntry();
            if (first.getValue() > watermark) {
                ranges.put(watermark + 1, first.getValue());
                return;
            }
            first = ranges.firstEntry();
        }
    }

    boolean contains(long seq) {
        Map.Entry<Long, Long> e = ranges.floorEntry(seq);
        return e != null && e.getValue() >= seq;
    }

    /** Number of stored intervals — the memory this set costs, whatever the numbers are. */
    int intervalCount() {
        return ranges.size();
    }

    boolean isEmpty() {
        return ranges.isEmpty();
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("[");
        ranges.forEach((s, e) -> {
            if (sb.length() > 1) sb.append(", ");
            sb.append(s);
            if (!s.equals(e)) sb.append("..").append(e);
        });
        return sb.append(']').toString();
    }
}
