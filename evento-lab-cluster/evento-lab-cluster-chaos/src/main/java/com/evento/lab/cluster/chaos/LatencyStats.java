package com.evento.lab.cluster.chaos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Latency percentiles and outcome counts over command records. */
public record LatencyStats(int count, int acked, int failed, long p50, long p95, long p99, long max,
                           Map<String, Integer> kinds) {

    public static LatencyStats of(Collection<RequestRecord> records) {
        var lat = new ArrayList<Long>();
        var kinds = new TreeMap<String, Integer>();
        int acked = 0;
        int failed = 0;
        for (var r : records) {
            if (!r.isCommand()) continue;
            lat.add(r.latencyMs());
            if (r.outcome() == RequestRecord.Outcome.ACK) acked++;
            else {
                failed++;
                kinds.merge(r.kind() == null ? "unknown" : r.kind(), 1, Integer::sum);
            }
        }
        lat.sort(Long::compare);
        return new LatencyStats(lat.size(), acked, failed, pct(lat, 50), pct(lat, 95), pct(lat, 99),
                lat.isEmpty() ? 0 : lat.getLast(), kinds);
    }

    /** Nearest-rank percentile of a sorted list; 0 when empty. */
    public static long pct(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, rank - 1)));
    }

    public static LatencyStats overall(Collection<RequestRecord> records) {
        return of(records);
    }

    public static Map<String, LatencyStats> byRegion(Collection<RequestRecord> records) {
        var groups = new TreeMap<String, List<RequestRecord>>();
        for (var r : records) {
            if (r.isCommand()) groups.computeIfAbsent(r.region(), k -> new ArrayList<>()).add(r);
        }
        var out = new TreeMap<String, LatencyStats>();
        groups.forEach((k, v) -> out.put(k, of(v)));
        return out;
    }

    @Override
    public String toString() {
        return "n=" + count + " ack=" + acked + " failed=" + failed + " p50=" + p50 + "ms p95=" + p95
                + "ms p99=" + p99 + "ms max=" + max + "ms" + (kinds.isEmpty() ? "" : " failures=" + kinds);
    }
}
