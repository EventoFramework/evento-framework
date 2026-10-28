package com.evento.lab.multievent;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class BasketCreated extends DomainEvent {
    private String basketId;

    public BasketCreated() {}

    public BasketCreated(String basketId) {
        this.basketId = basketId;
    }

    public String getBasketId() { return basketId; }
}
