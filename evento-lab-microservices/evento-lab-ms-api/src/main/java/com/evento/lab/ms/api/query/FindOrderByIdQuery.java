package com.evento.lab.ms.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.ms.api.view.OrderView;

public class FindOrderByIdQuery extends Query<Single<OrderView>> {

    private String orderId;

    public FindOrderByIdQuery() {
    }

    public FindOrderByIdQuery(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
