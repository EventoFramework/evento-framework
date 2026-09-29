package com.evento.lab.cluster.chaos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The structured outcome of one invariant: what a correct system shows, what
 * was observed, and the first violations. Nothing is asserted here; a
 * scenario decides what a FAIL means for it (a documented known defect is a
 * FAIL that matches the expectation).
 */
public record InvariantResult(
        String id,
        String name,
        Status status,
        String expected,
        String observed,
        Map<String, Object> metrics,
        int violationCount,
        List<String> violations) {

    public enum Status { PASS, FAIL, OBSERVED, SKIPPED, ERROR }

    public static final int MAX_LISTED = 50;

    public static InvariantResult of(String id, String name, String expected, String observed,
                                     Map<String, Object> metrics, List<String> violations) {
        var listed = violations.size() > MAX_LISTED ? violations.subList(0, MAX_LISTED) : violations;
        return new InvariantResult(id, name, violations.isEmpty() ? Status.PASS : Status.FAIL,
                expected, observed, new LinkedHashMap<>(metrics), violations.size(), List.copyOf(listed));
    }

    public static InvariantResult observedOnly(String id, String name, String expected, String observed,
                                               Map<String, Object> metrics) {
        return new InvariantResult(id, name, Status.OBSERVED, expected, observed,
                new LinkedHashMap<>(metrics), 0, List.of());
    }

    public static InvariantResult skipped(String id, String name, String why) {
        return new InvariantResult(id, name, Status.SKIPPED, "", why, Map.of(), 0, List.of());
    }

    public static InvariantResult error(String id, String name, String why) {
        return new InvariantResult(id, name, Status.ERROR, "", why, Map.of(), 0, List.of());
    }

    public boolean passed() {
        return status == Status.PASS || status == Status.OBSERVED;
    }
}
