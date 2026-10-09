package com.evento.lab.cluster.support.web;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last {@value #CAPACITY} latencies of each operation this JVM timed itself (the gateway's
 * {@code send} per HTTP operation), with exact percentiles over them. Plain lab code, no
 * framework type, so the same source builds against the framework of any ref.
 */
@Component
public class LatencySamples {

    static final int CAPACITY = 20_000;

    private final Map<String, Ring> rings = new ConcurrentHashMap<>();

    public void record(String operation, long nanos) {
        rings.computeIfAbsent(operation, k -> new Ring()).add(nanos);
    }

    /** Per operation: count, p50, p95, p99, max and mean, in milliseconds. */
    public Map<String, Map<String, Object>> summary() {
        var out = new TreeMap<String, Map<String, Object>>();
        rings.forEach((op, ring) -> out.put(op, ring.summary()));
        return out;
    }

    public void reset() {
        rings.clear();
    }

    private static final class Ring {
        private final long[] values = new long[CAPACITY];
        private long written;

        synchronized void add(long nanos) {
            values[(int) (written++ % CAPACITY)] = nanos;
        }

        Map<String, Object> summary() {
            long[] v;
            synchronized (this) {
                v = Arrays.copyOf(values, (int) Math.min(written, CAPACITY));
            }
            Arrays.sort(v);
            var m = new LinkedHashMap<String, Object>();
            m.put("count", v.length);
            if (v.length == 0) return m;
            m.put("p50Ms", pct(v, 0.50));
            m.put("p95Ms", pct(v, 0.95));
            m.put("p99Ms", pct(v, 0.99));
            m.put("maxMs", v[v.length - 1] / 1e6);
            m.put("meanMs", Arrays.stream(v).average().orElse(0) / 1e6);
            return m;
        }

        private static double pct(long[] sorted, double q) {
            int i = (int) Math.ceil(q * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, i))] / 1e6;
        }
    }
}
