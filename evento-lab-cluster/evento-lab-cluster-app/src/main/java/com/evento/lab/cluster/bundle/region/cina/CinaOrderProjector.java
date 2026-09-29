package com.evento.lab.cluster.bundle.region.cina;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.lab.cluster.api.event.OrderCompletedEvent;
import com.evento.lab.cluster.api.event.OrderCreatedEvent;
import com.evento.lab.cluster.api.event.OrderItemAddedEvent;
import com.evento.lab.cluster.bundle.region.RegionOrderProjection;
import com.evento.lab.cluster.store.OrderViewStore;

/** CINA's read model: bundle {@code query-cina}, context {@code CINA}. See {@link RegionOrderProjection}. */
@Projector(version = 1)
public class CinaOrderProjector extends RegionOrderProjection {

    public CinaOrderProjector(OrderViewStore view) {
        super(view);
    }

    @EventHandler
    void on(OrderCreatedEvent e, PublishedEvent pe) throws Exception {
        created(e, pe);
    }

    @EventHandler
    void on(OrderItemAddedEvent e, PublishedEvent pe) throws Exception {
        itemAdded(e, pe);
    }

    @EventHandler
    void on(OrderCompletedEvent e, PublishedEvent pe) throws Exception {
        completed(e, pe);
    }
}
