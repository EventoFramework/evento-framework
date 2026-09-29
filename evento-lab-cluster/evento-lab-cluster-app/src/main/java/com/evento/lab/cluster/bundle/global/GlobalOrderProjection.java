package com.evento.lab.cluster.bundle.global;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.cluster.api.query.FindGlobalOrderQuery;
import com.evento.lab.cluster.api.view.OrderViewDto;
import com.evento.lab.cluster.store.OrderViewStore;

import java.util.NoSuchElementException;

/** Serves {@code order_view_global} over the query bus to the region gateways. */
@Projection
public class GlobalOrderProjection {

    private final OrderViewStore view;

    public GlobalOrderProjection(OrderViewStore view) {
        this.view = view;
    }

    @QueryHandler
    Single<OrderViewDto> query(FindGlobalOrderQuery q) throws Exception {
        return Single.of(view.find(q.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("order not found: " + q.getOrderId())));
    }
}
