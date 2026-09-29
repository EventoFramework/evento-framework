package com.evento.lab.cluster.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.cluster.api.view.OrderViewDto;

/**
 * An order as the caller's cell sees it: answered by any order-query
 * deployment of the cell from the cell's local {@code order_view}. With the
 * broker's {@code locality-preferred} pick strategy the query never leaves
 * the cell.
 */
public class FindOrderQuery extends Query<Single<OrderViewDto>> {

    private String orderId;

    public FindOrderQuery() {
    }

    public FindOrderQuery(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
