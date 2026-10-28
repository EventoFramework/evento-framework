package com.evento.lab.multievent;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateBasket extends DomainCommand {
    private String basketId;

    public CreateBasket() {}

    public CreateBasket(String basketId) {
        this.basketId = basketId;
    }

    @Override
    public String getAggregateId() { return basketId; }

    public String getBasketId() { return basketId; }
}
