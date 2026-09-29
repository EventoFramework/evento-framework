package com.evento.lab.cluster.bundle.saga;

import com.evento.common.modeling.annotations.component.Observer;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.lab.cluster.api.event.OrderCompletedEvent;
import com.evento.lab.cluster.api.event.OrderCreatedEvent;
import com.evento.lab.cluster.api.event.OrderItemAddedEvent;
import com.evento.lab.cluster.api.event.PaymentProcessedEvent;
import com.evento.lab.cluster.store.SideEffectStore;

/**
 * Records every order event it sees into {@code observed_events}, keyed by the
 * event sequence number. A second delivery bumps {@code deliveries}: the
 * framework's observer dedupe store should make that impossible, so any row
 * with {@code deliveries > 1} is a duplicated side effect.
 */
@Observer(version = 1)
public class ClusterEventObserver {

    private final SideEffectStore sideEffects;

    public ClusterEventObserver(SideEffectStore sideEffects) {
        this.sideEffects = sideEffects;
    }

    @EventHandler
    void on(OrderCreatedEvent e, PublishedEvent pe) throws Exception {
        record(pe, e.getOrderId(), e.getContext());
    }

    @EventHandler
    void on(OrderItemAddedEvent e, PublishedEvent pe) throws Exception {
        record(pe, e.getOrderId(), e.getContext());
    }

    @EventHandler
    void on(OrderCompletedEvent e, PublishedEvent pe) throws Exception {
        record(pe, e.getOrderId(), e.getContext());
    }

    @EventHandler
    void on(PaymentProcessedEvent e, PublishedEvent pe) throws Exception {
        record(pe, e.getOrderId(), e.getContext());
    }

    private void record(PublishedEvent pe, String orderId, String context) throws Exception {
        sideEffects.recordObserved(pe.getEventSequenceNumber(), pe.getEventName(), orderId, context);
    }
}
