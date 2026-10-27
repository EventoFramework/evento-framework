package com.evento.bench.evento.api;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateOrder extends DomainCommand {
    private String orderId;

    public CreateOrder() {
    }

    public CreateOrder(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
