package com.evento.lab.ms.saga.saga;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe static store for saga state transitions, used by integration tests
 * to assert saga state without direct access to the internal saga state store.
 */
public final class MsSagaStore {

    private static final ConcurrentHashMap<String, String> sagaStatuses = new ConcurrentHashMap<>();

    public static void record(String orderId, String status) {
        sagaStatuses.put(orderId, status);
    }

    public static String getStatus(String orderId) {
        return sagaStatuses.get(orderId);
    }

    public static void reset() {
        sagaStatuses.clear();
    }

    private MsSagaStore() {
    }
}
