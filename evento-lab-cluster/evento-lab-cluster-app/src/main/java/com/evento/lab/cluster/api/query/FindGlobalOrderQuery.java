package com.evento.lab.cluster.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.cluster.api.view.OrderViewDto;

public class FindGlobalOrderQuery extends Query<Single<OrderViewDto>> {

    private String orderId;

    public FindGlobalOrderQuery() {
    }

    public FindGlobalOrderQuery(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
