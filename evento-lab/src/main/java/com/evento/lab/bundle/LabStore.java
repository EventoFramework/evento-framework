package com.evento.lab.bundle;

import com.evento.lab.view.OrderView;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LabStore {

    private static final ConcurrentHashMap<String, OrderView> orders = new ConcurrentHashMap<>();
    private static final List<String> observedEvents = new CopyOnWriteArrayList<>();

    public static void put(OrderView v) {
        orders.put(v.getOrderId(), v);
    }

    public static OrderView get(String id) {
        return orders.get(id);
    }

    public static List<OrderView> getAll() {
        return List.copyOf(orders.values());
    }

    public static List<String> getObservedEvents() {
        return observedEvents;
    }

    public static void recordEvent(String name) {
        observedEvents.add(name);
    }

    public static void reset() {
        orders.clear();
        observedEvents.clear();
    }

    private LabStore() {
    }
}
