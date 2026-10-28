package com.evento.lab.multievent;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class CouponUsed extends DomainEvent {
    private String basketId;

    public CouponUsed() {}

    public CouponUsed(String basketId) {
        this.basketId = basketId;
    }

    public String getBasketId() { return basketId; }
}
