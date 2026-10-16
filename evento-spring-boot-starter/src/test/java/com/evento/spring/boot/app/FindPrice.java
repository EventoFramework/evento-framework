package com.evento.spring.boot.app;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;

public class FindPrice extends Query<Single<PriceView>> {
    private String item;

    public FindPrice() {
    }

    public FindPrice(String item) {
        this.item = item;
    }

    public String getItem() { return item; }
}
