package com.evento.test.broker.demo;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateOrder extends DomainCommand {
    String orderId;
    public CreateOrder() {}
    public CreateOrder(String orderId) { this.orderId = orderId; }
    @Override public String getAggregateId() { return orderId; }
}
