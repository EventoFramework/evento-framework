package com.evento.lab.multievent;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class BasketCheckedOut extends DomainEvent {
    private String basketId;

    public BasketCheckedOut() {}

    public BasketCheckedOut(String basketId) {
        this.basketId = basketId;
    }

    public String getBasketId() { return basketId; }
}
