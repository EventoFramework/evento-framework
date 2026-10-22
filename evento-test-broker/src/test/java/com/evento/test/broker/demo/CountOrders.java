package com.evento.test.broker.demo;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;

public class CountOrders extends Query<Single<OrderCount>> {
}
