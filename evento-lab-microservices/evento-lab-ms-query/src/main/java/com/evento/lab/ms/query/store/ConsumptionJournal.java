package com.evento.lab.ms.query.store;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The orders {@code OrderProjector} applied, per context, in the order it applied them, whichever
 * replica did. The assertion surface of the high-availability scenario: a consumer replicated
 * across instances must apply each event of its context once, in the order of the stream.
 */
public final class ConsumptionJournal {

    private static final Map<String, List<String>> byContext = new ConcurrentHashMap<>();

    public static void record(String context, String orderId) {
        byContext.computeIfAbsent(context == null ? "*" : context, c -> new CopyOnWriteArrayList<>()).add(orderId);
    }

    public static List<String> of(String context) {
        return List.copyOf(byContext.getOrDefault(context, List.of()));
    }

    public static void reset() {
        byContext.clear();
    }

    private ConsumptionJournal() {
    }
}
