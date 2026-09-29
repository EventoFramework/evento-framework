package com.evento.lab.cluster.bundle.region;

import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.lab.cluster.api.event.OrderCompletedEvent;
import com.evento.lab.cluster.api.event.OrderCreatedEvent;
import com.evento.lab.cluster.api.event.OrderItemAddedEvent;
import com.evento.lab.cluster.store.OrderViewStore;

/**
 * The logic of a region's own read model, shared by the seven per-region
 * projector classes ({@code region.<r>.<R>OrderProjector}).
 *
 * <p>Why seven classes and not one {@code RegionOrderProjector} reused by the
 * seven {@code query-<region>} bundles: the server keys {@code core__component}
 * by the component's simple class name (N1 in {@code FINDINGS.md}). With a shared
 * name the component row, its handler rows and, on a NodeLeft, every sibling's
 * {@code core__consumer} rows belong to whichever bundle registered last, so
 * restarting one region wiped the catalog entries of all seven. A unique simple
 * name per region is the lab-side workaround.
 *
 * <p>It stores the context of every event it receives without filtering it
 * again: if the server's context match leaks another context in (G15, LIKE with
 * an unescaped {@code _}), the row shows it and I4 catches it.
 *
 * <p>Deliberately not annotated: each subclass is the {@code @Projector} and
 * declares its own {@code @EventHandler} methods, so handler discovery never
 * depends on inherited methods.
 */
public abstract class RegionOrderProjection {

    private final OrderViewStore view;

    protected RegionOrderProjection(OrderViewStore view) {
        this.view = view;
    }

    protected void created(OrderCreatedEvent e, PublishedEvent pe) throws Exception {
        view.apply(OrderViewStore.Change.CREATED, e.getOrderId(), e.getContext(), pe.getEventSequenceNumber(), 0);
    }

    protected void itemAdded(OrderItemAddedEvent e, PublishedEvent pe) throws Exception {
        view.apply(OrderViewStore.Change.ITEM_ADDED, e.getOrderId(), e.getContext(), pe.getEventSequenceNumber(), e.getQty());
    }

    protected void completed(OrderCompletedEvent e, PublishedEvent pe) throws Exception {
        view.apply(OrderViewStore.Change.COMPLETED, e.getOrderId(), e.getContext(), pe.getEventSequenceNumber(), 0);
    }
}
