package com.evento.lab.cluster.bundle.region;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.lab.cluster.api.event.OrderCompletedEvent;
import com.evento.lab.cluster.api.event.OrderCreatedEvent;
import com.evento.lab.cluster.api.event.OrderItemAddedEvent;
import com.evento.lab.cluster.store.OrderViewStore;

/**
 * A region's own read model. Each region JVM runs it in bundle
 * {@code query-<region>} with {@code setComponentContexts(RegionOrderProjector, REGION)},
 * giving consumerId {@code query-<region>_RegionOrderProjector_1_<REGION>}.
 *
 * <p>It stores the context of every event it receives without filtering it
 * again: if the server's context match leaks another context in (G15, LIKE with
 * an unescaped {@code _}), the row shows it and I4 catches it.
 */
@Projector(version = 1)
public class RegionOrderProjector {

    private final OrderViewStore view;

    public RegionOrderProjector(OrderViewStore view) {
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
}
