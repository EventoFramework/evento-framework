package com.evento.application.client.metrics;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.LongAccumulator;

/**
 * A lock-free latency histogram for a bundle, which has no Micrometer: log-linear buckets
 * (32 per power of two, so a percentile is off by at most ~3%), nanosecond values, a fixed
 * ~15 KB of counters, one atomic increment per sample. Percentiles cover everything recorded
 * since creation or the last {@link #reset()}.
 *
 * <p>The broker has real timers ({@code evento.server.command.phase}); this is the bundle-side
 * counterpart for the numbers only a bundle sees (its own request round trips, its handlers'
 * execution time), exposed through plain accessors so an application can publish them with
 * whatever metrics library it has.
 */
public final class LatencyHistogram {

    private static final int SUB_BITS = 5;
    private static final int SUB = 1 << SUB_BITS;
    /** Linear buckets below {@link #SUB}, then {@link #SUB} per power of two up to 2^62. */
    private static final int BUCKETS = SUB + (62 - SUB_BITS + 1) * SUB;

    private final AtomicLongArray counts = new AtomicLongArray(BUCKETS);
    private final LongAdder count = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final LongAccumulator maxNanos = new LongAccumulator(Math::max, 0L);

    /** Records one sample; negative values count as 0. */
    public void record(long nanos) {
        long v = Math.max(0L, nanos);
        counts.incrementAndGet(index(v));
        count.increment();
        totalNanos.add(v);
        maxNanos.accumulate(v);
    }

    public long count() {
        return count.sum();
    }

    public long totalNanos() {
        return totalNanos.sum();
    }

    public long maxNanos() {
        return maxNanos.get();
    }

    /**
     * The value at quantile {@code q} (0..1), in nanoseconds: the middle of the bucket holding
     * the {@code ceil(q * count)}-th smallest sample; 0 when empty.
     */
    public long percentileNanos(double q) {
        long[] snapshot = new long[BUCKETS];
        long n = 0;
        for (int i = 0; i < BUCKETS; i++) {
            snapshot[i] = counts.get(i);
            n += snapshot[i];
        }
        return percentile(snapshot, n, q);
    }

    /** count, mean, p50, p95, p99 and max, read from one pass over the buckets. */
    public Snapshot snapshot() {
        long[] snapshot = new long[BUCKETS];
        long n = 0;
        for (int i = 0; i < BUCKETS; i++) {
            snapshot[i] = counts.get(i);
            n += snapshot[i];
        }
        long total = totalNanos.sum();
        return new Snapshot(n, n == 0 ? 0 : total / n, percentile(snapshot, n, 0.50), percentile(snapshot, n, 0.95),
                percentile(snapshot, n, 0.99), maxNanos.get());
    }

    /**
     * Starts a new window. Not atomic with concurrent {@link #record} calls: a sample racing the
     * reset may be counted in one field and not another, which is acceptable for monitoring.
     */
    public void reset() {
        for (int i = 0; i < BUCKETS; i++) counts.set(i, 0);
        count.reset();
        totalNanos.reset();
        maxNanos.reset();
    }

    private static long percentile(long[] buckets, long n, double q) {
        if (n == 0) return 0;
        long rank = Math.max(1, (long) Math.ceil(Math.min(1.0, Math.max(0.0, q)) * n));
        long seen = 0;
        for (int i = 0; i < buckets.length; i++) {
            seen += buckets[i];
            if (seen >= rank) return middle(i);
        }
        return middle(buckets.length - 1);
    }

    static int index(long v) {
        if (v < SUB) return (int) v;
        int msb = 63 - Long.numberOfLeadingZeros(v);
        int shift = msb - SUB_BITS;
        int sub = (int) (v >>> shift) & (SUB - 1);
        return SUB + shift * SUB + sub;
    }

    static long lowerBound(int index) {
        if (index < SUB) return index;
        int shift = (index - SUB) / SUB;
        int sub = (index - SUB) % SUB;
        return ((long) (SUB + sub)) << shift;
    }

    private static long middle(int index) {
        if (index < SUB) return index;
        int shift = (index - SUB) / SUB;
        return lowerBound(index) + ((1L << shift) >> 1);
    }

    /** A summary of a histogram; durations in nanoseconds. */
    public record Snapshot(long count, long meanNanos, long p50Nanos, long p95Nanos, long p99Nanos, long maxNanos) {

        /** {@code count=… p50=…ms p95=…ms p99=…ms max=…ms}. */
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "count=%d mean=%.3fms p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms",
                    count, meanNanos / 1e6, p50Nanos / 1e6, p95Nanos / 1e6, p99Nanos / 1e6, maxNanos / 1e6);
        }
    }
}
