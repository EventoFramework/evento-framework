package com.evento.lab.cluster.query.bundle;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.cluster.api.query.FindGlobalOrderQuery;
import com.evento.lab.cluster.api.query.FindOrderQuery;
import com.evento.lab.cluster.api.query.FindPriceQuery;
import com.evento.lab.cluster.api.view.OrderViewDto;
import com.evento.lab.cluster.api.view.PriceViewDto;
import com.evento.lab.cluster.query.LocalCatalog;
import com.evento.lab.cluster.query.OrderViews;

import java.util.NoSuchElementException;

/**
 * The query side, identical in every order-query deployment whatever its
 * contexts: orders and prices are answered from the deployment's own cell
 * database, which holds every region of the cell and the fanned-out catalog.
 * With the broker's {@code locality-preferred} pick strategy and
 * {@code EVENTO_ZONE} set, a gateway's query is answered inside its cell.
 */
@Projection
public class OrderProjection {

    private final OrderViews views;
    private final LocalCatalog catalog;

    public OrderProjection(OrderViews views, LocalCatalog catalog) {
        this.views = views;
        this.catalog = catalog;
    }

    @QueryHandler
    Single<OrderViewDto> query(FindOrderQuery q) throws Exception {
        return Single.of(views.local().find(q.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("order not found: " + q.getOrderId())));
    }

    @QueryHandler
    Single<OrderViewDto> query(FindGlobalOrderQuery q) throws Exception {
        return Single.of(views.global().find(q.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("order not found: " + q.getOrderId())));
    }

    @QueryHandler
    Single<PriceViewDto> query(FindPriceQuery q) throws Exception {
        return Single.of(catalog.store().find(q.getSku())
                .orElseThrow(() -> new NoSuchElementException("price not found: " + q.getSku())));
    }
}
