package com.evento.lab.ms.query.store;

import com.evento.lab.ms.api.view.OrderView;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class OrderViewStore {

    private static final ConcurrentHashMap<String, OrderView> orders = new ConcurrentHashMap<>();

    public static void put(OrderView v) {
        orders.put(v.getOrderId(), v);
    }

    public static OrderView get(String orderId) {
        return orders.get(orderId);
    }

    public static List<OrderView> getAll() {
        return List.copyOf(orders.values());
    }

    public static void reset() {
        orders.clear();
    }

    private OrderViewStore() {
    }
}
