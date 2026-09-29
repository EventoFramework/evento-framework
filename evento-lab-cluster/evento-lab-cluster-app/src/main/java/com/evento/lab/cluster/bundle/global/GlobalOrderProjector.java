package com.evento.lab.cluster.bundle.global;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.lab.cluster.api.event.OrderCompletedEvent;
import com.evento.lab.cluster.api.event.OrderCreatedEvent;
import com.evento.lab.cluster.api.event.OrderItemAddedEvent;
import com.evento.lab.cluster.api.event.PaymentProcessedEvent;
import com.evento.lab.cluster.store.OrderViewStore;

/**
 * The worldwide projector. No context is configured for it, so it consumes
 * every context ({@code *}). The same bundle id runs in every cell, so the
 * three copies compete for one lane (G24): exactly one of them projects at a
 * time and the others stand by.
 */
@Projector(version = 1)
public class GlobalOrderProjector {

    private final OrderViewStore view;

    public GlobalOrderProjector(OrderViewStore view) {
        this.view = view;
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
