package com.evento.lab.ms.observer.store;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe static store for notifications sent via NotificationService.
 * Used by integration tests to assert notifications were dispatched.
 * Entry format: "channel:orderId:message"
 */
public final class MsNotificationLog {

    private static final CopyOnWriteArrayList<String> entries = new CopyOnWriteArrayList<>();

    public static void record(String entry) {
        entries.add(entry);
    }

    public static List<String> getAll() {
        return Collections.unmodifiableList(entries);
    }

    public static void reset() {
        entries.clear();
    }

    private MsNotificationLog() {
    }
}
