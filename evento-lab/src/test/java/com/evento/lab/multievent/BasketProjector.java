package com.evento.lab.multievent;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Projector(version = 1)
public class BasketProjector {

    static final List<String> seen = new CopyOnWriteArrayList<>();

    static void reset() { seen.clear(); }

    @EventHandler
    void on(ItemReserved e) { seen.add("ItemReserved:" + e.getBasketId() + ":" + e.getIndex()); }

    @EventHandler
    void on(BasketCheckedOut e) { seen.add("BasketCheckedOut:" + e.getBasketId()); }

    @EventHandler
    void on(CouponUsed e) { seen.add("CouponUsed:" + e.getBasketId()); }
}
