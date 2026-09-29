package com.evento.lab.cluster.query;

import com.evento.lab.cluster.support.store.OrderViewStore;

/**
 * The order tables one order-query deployment touches.
 *
 * @param writer where {@code OrderProjector} writes: the cell's {@code order_view} for a
 *               regional deployment, {@code order_view_global} for the wildcard one
 * @param local  the cell's {@code order_view}, read by {@code FindOrderQuery}
 * @param global {@code order_view_global} (possibly in another cell), read by {@code FindGlobalOrderQuery}
 */
public record OrderViews(OrderViewStore writer, OrderViewStore local, OrderViewStore global) {
}
