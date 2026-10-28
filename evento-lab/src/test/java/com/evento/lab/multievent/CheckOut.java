package com.evento.lab.multievent;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CheckOut extends DomainCommand {
    private String basketId;
    private int items;
    private boolean coupon;

    public CheckOut() {}

    public CheckOut(String basketId, int items, boolean coupon) {
        this.basketId = basketId;
        this.items = items;
        this.coupon = coupon;
    }

    @Override
    public String getAggregateId() { return basketId; }

    public String getBasketId() { return basketId; }
    public int getItems() { return items; }
    public boolean isCoupon() { return coupon; }
}
