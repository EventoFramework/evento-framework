package com.evento.lab.multievent;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ItemReserved extends DomainEvent {
    private String basketId;
    private int index;

    public ItemReserved() {}

    public ItemReserved(String basketId, int index) {
        this.basketId = basketId;
        this.index = index;
    }

    public String getBasketId() { return basketId; }
    public int getIndex() { return index; }
}
