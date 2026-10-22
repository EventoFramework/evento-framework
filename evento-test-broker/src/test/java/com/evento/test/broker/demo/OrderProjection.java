package com.evento.test.broker.demo;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;

@Projection
public class OrderProjection {
    @QueryHandler
    Single<OrderCount> query(CountOrders q) {
        return Single.of(new OrderCount(Orders.SEEN.size()));
    }
}
