package com.evento.lab.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.api.view.OrderView;

public class FindOrderByIdQuery extends Query<Single<OrderView>> {

    private String orderId;
    private boolean failBeforeHandling;
    private boolean failAfterHandling;

    public FindOrderByIdQuery() {
    }

    public FindOrderByIdQuery(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public boolean isFailBeforeHandling() { return failBeforeHandling; }
    public void setFailBeforeHandling(boolean b) { this.failBeforeHandling = b; }
    public boolean isFailAfterHandling() { return failAfterHandling; }
    public void setFailAfterHandling(boolean b) { this.failAfterHandling = b; }
}
