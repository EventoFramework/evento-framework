package com.evento.spring.boot.app;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;

@Projection
public class PriceProjection {

    private final PriceBook prices;

    public PriceProjection(PriceBook prices) {
        this.prices = prices;
    }

    @QueryHandler
    Single<PriceView> on(FindPrice query) {
        return Single.of(new PriceView(query.getItem(), prices.priceOf(query.getItem())));
    }
}
