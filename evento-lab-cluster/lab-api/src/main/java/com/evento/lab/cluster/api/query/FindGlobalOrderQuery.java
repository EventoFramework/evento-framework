package com.evento.lab.cluster.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.cluster.api.view.OrderViewDto;

/**
 * An order as the world-wide read model {@code order_view_global} sees it.
 * That table is written by the one wildcard ({@code *}) deployment of
 * order-query and lives in one database; order-query deployments of other
 * cells read it over the WAN ({@code GLOBAL_VIEW_DB_URL}).
 */
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
