package com.evento.lab.bundle.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.api.query.FindOrderByIdQuery;
import com.evento.lab.api.query.ListOrdersQuery;
import com.evento.lab.api.view.OrderView;

import java.util.NoSuchElementException;

@Projection
public class LabProjection {

    @QueryHandler
    Single<OrderView> query(FindOrderByIdQuery q) {
        var v = LabStore.get(q.getOrderId());
        if (v == null) throw new NoSuchElementException("order not found: " + q.getOrderId());
        return Single.of(v);
    }

    @QueryHandler
    Multiple<OrderView> query(ListOrdersQuery q) {
        return Multiple.of(LabStore.getAll());
    }
}
