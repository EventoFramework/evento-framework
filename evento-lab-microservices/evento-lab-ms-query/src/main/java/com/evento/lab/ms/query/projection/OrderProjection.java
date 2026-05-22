package com.evento.lab.ms.query.projection;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.ms.api.query.FindOrderByIdQuery;
import com.evento.lab.ms.api.query.ListOrdersQuery;
import com.evento.lab.ms.api.view.OrderView;
import com.evento.lab.ms.query.store.OrderViewStore;

import java.util.NoSuchElementException;

@Projection
public class OrderProjection {

    @QueryHandler
    Single<OrderView> query(FindOrderByIdQuery q) {
        var v = OrderViewStore.get(q.getOrderId());
        if (v == null) throw new NoSuchElementException("order not found: " + q.getOrderId());
        return Single.of(v);
    }

    @QueryHandler
    Multiple<OrderView> query(ListOrdersQuery q) {
        return Multiple.of(OrderViewStore.getAll());
    }
}
