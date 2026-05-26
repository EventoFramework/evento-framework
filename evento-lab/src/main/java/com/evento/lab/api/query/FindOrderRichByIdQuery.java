package com.evento.lab.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.api.view.OrderRichView;

public class FindOrderRichByIdQuery extends Query<Single<OrderRichView>> {

    private String orderId;

    public FindOrderRichByIdQuery() {}

    public FindOrderRichByIdQuery(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
