package com.evento.lab.cluster.observer.bundle;

import com.evento.common.modeling.annotations.component.Observer;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.lab.cluster.api.event.OrderCompletedEvent;
import com.evento.lab.cluster.api.event.OrderCreatedEvent;
import com.evento.lab.cluster.api.event.OrderItemAddedEvent;
import com.evento.lab.cluster.api.event.PaymentProcessedEvent;
import com.evento.lab.cluster.observer.ObserverLane;
import com.evento.lab.cluster.support.store.SideEffectStore;

/**
 * The customer-notification side effect, recorded into {@code observed_events}
 * keyed by event sequence number. A second delivery bumps {@code deliveries}:
 * the framework's observer dedupe store should make that impossible, so any
 * row with {@code deliveries > 1} is a duplicated side effect (I2).
 *
 * <p>Its contexts come from {@code EVENTO_CONTEXTS}, one engine per context.
 * Deliveries are counted per lane (the bundle id), so a {@code *} observer and
 * a regional one seeing the same event are two notifications, not a duplicate.
 */
@Observer(version = 1)
public class NotificationObserver {

    private final SideEffectStore sideEffects;
    private final String lane;

    public NotificationObserver(ObserverLane lane) {
        this.sideEffects = lane.sideEffects();
        this.lane = lane.name();
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
        sideEffects.recordObserved(lane, pe.getEventSequenceNumber(), pe.getEventName(), orderId, context);
    }
}
