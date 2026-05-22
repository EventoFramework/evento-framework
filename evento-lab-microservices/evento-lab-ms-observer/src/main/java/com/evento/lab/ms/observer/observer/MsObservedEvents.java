package com.evento.lab.ms.observer.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe static store for events observed by OrderObserver.
 * Used by integration tests to assert events were received without Spring context.
 */
public final class MsObservedEvents {

    private static final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();

    public static void record(String eventName) {
        events.add(eventName);
    }

    public static List<String> getAll() {
        return List.copyOf(events);
    }

    public static void reset() {
        events.clear();
    }

    private MsObservedEvents() {
    }
}
