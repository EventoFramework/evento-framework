package com.evento.lab.bundle;

import com.evento.lab.api.view.OrderRichView;
import com.evento.lab.api.view.OrderView;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class LabStore {

    private static final ConcurrentHashMap<String, OrderView> orders = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, OrderRichView> richOrders = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> stressCounts = new ConcurrentHashMap<>();
    private static final List<String> observedEvents = new CopyOnWriteArrayList<>();

    // ---- OrderView ----

    public static void put(OrderView v) { orders.put(v.getOrderId(), v); }
    public static OrderView get(String id) { return orders.get(id); }
    public static List<OrderView> getAll() { return List.copyOf(orders.values()); }

    // ---- OrderRichView ----

    public static void putRich(OrderRichView v) { richOrders.put(v.getOrderId(), v); }
    public static OrderRichView getRich(String id) { return richOrders.get(id); }
    public static List<OrderRichView> getAllRich() { return List.copyOf(richOrders.values()); }

    // ---- Stress counters ----

    public static void incrementStress(String key) {
        stressCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    public static long getStressCount(String key) {
        var c = stressCounts.get(key);
        return c == null ? 0L : c.get();
    }

    // ---- Observer log ----

    public static List<String> getObservedEvents() { return observedEvents; }
    public static void recordEvent(String name) { observedEvents.add(name); }

    // ---- Test lifecycle ----

    public static void reset() {
        orders.clear();
        richOrders.clear();
        stressCounts.clear();
        observedEvents.clear();
    }

    private LabStore() {}
}
