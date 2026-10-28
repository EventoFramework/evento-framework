package com.evento.lab.multievent;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class Sneak extends DomainCommand {
    private String basketId;

    public Sneak() {}

    public Sneak(String basketId) {
        this.basketId = basketId;
    }

    @Override
    public String getAggregateId() { return basketId; }

    public String getBasketId() { return basketId; }
}
