package com.evento.lab.cluster.query.bundle;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.lab.cluster.api.event.OrderCompletedEvent;
import com.evento.lab.cluster.api.event.OrderCreatedEvent;
import com.evento.lab.cluster.api.event.OrderItemAddedEvent;
import com.evento.lab.cluster.api.event.PaymentProcessedEvent;
import com.evento.lab.cluster.query.OrderViews;
import com.evento.lab.cluster.support.store.OrderViewStore;

/**
 * The one order projector of the lab. Which orders it sees is not in this
 * class: it is the deployment's {@code EVENTO_CONTEXTS}, applied with
 * {@code setComponentContexts(OrderProjector.class, ...)}, and the bundle
 * starts one consumer engine per context ({@code order-query_OrderProjector_1_EUROPA},
 * {@code ..._AFRICA}, ...). A regional deployment writes the cell's
 * {@code order_view}; the wildcard ({@code *}) deployment writes
 * {@code order_view_global}.
 */
@Projector(version = 1)
public class OrderProjector {

    private final OrderViewStore view;

    public OrderProjector(OrderViews views) {
        this.view = views.writer();
    }

    @EventHandler
    void on(OrderCreatedEvent e, PublishedEvent pe) throws Exception {
        view.apply(OrderViewStore.Change.CREATED, e.getOrderId(), e.getContext(), pe.getEventSequenceNumber(), 0);
    }

    @EventHandler
    void on(OrderItemAddedEvent e, PublishedEvent pe) throws Exception {
        view.apply(OrderViewStore.Change.ITEM_ADDED, e.getOrderId(), e.getContext(), pe.getEventSequenceNumber(), e.getQty());
    }

    @EventHandler
    void on(OrderCompletedEvent e, PublishedEvent pe) throws Exception {
        view.apply(OrderViewStore.Change.COMPLETED, e.getOrderId(), e.getContext(), pe.getEventSequenceNumber(), 0);
    }

    @EventHandler
    void on(PaymentProcessedEvent e, PublishedEvent pe) throws Exception {
        view.apply(OrderViewStore.Change.PAID, e.getOrderId(), e.getContext(), pe.getEventSequenceNumber(), 0);
    }
}
