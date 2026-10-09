package com.evento.application.client.metrics;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link LatencyHistogram}s by name (a payload type, a handler type). Names come from closed sets
 * (the payload types a bundle sends, three handler types), but the map is bounded anyway: past
 * {@code maxNames} distinct names, new ones share the histogram {@value #OVERFLOW}.
 */
public final class LatencyRecorder {

    /** The histogram that names past the bound share. */
    public static final String OVERFLOW = "_other";

    private final ConcurrentHashMap<String, LatencyHistogram> histograms = new ConcurrentHashMap<>();
    private final int maxNames;

    public LatencyRecorder(int maxNames) {
        this.maxNames = maxNames;
    }

    public void record(String name, long nanos) {
        histogram(name).record(nanos);
    }

    /** The histogram of {@code name}, created on first use. */
    public LatencyHistogram histogram(String name) {
        var h = histograms.get(name);
        if (h != null) return h;
        if (histograms.size() >= maxNames) return histograms.computeIfAbsent(OVERFLOW, k -> new LatencyHistogram());
        return histograms.computeIfAbsent(name, k -> new LatencyHistogram());
    }

    /** Every histogram's summary, by name (sorted). */
    public Map<String, LatencyHistogram.Snapshot> snapshot() {
        var out = new TreeMap<String, LatencyHistogram.Snapshot>();
        histograms.forEach((name, h) -> out.put(name, h.snapshot()));
        return out;
    }

    /** Starts a new window on every histogram. */
    public void reset() {
        histograms.values().forEach(LatencyHistogram::reset);
    }
}
