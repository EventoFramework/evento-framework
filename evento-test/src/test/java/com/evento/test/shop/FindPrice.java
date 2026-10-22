package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.payload.View;
import com.evento.common.modeling.messaging.query.Single;

public class FindPrice extends Query<Single<PriceView>> {
    String item;
    public FindPrice() {}
    public FindPrice(String item) { this.item = item; }
}
