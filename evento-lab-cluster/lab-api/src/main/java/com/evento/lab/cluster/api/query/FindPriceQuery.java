package com.evento.lab.cluster.api.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.cluster.api.view.PriceViewDto;

/** A list price as the answering cell's local database holds it (fanned out by the global lane). */
public class FindPriceQuery extends Query<Single<PriceViewDto>> {

    private String sku;

    public FindPriceQuery() {
    }

    public FindPriceQuery(String sku) {
        this.sku = sku;
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
}
