package com.evento.test.shop;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateOrder extends DomainCommand {
    String orderId;
    int budget;
    public CreateOrder() {}
    public CreateOrder(String orderId, int budget) { this.orderId = orderId; this.budget = budget; }
    @Override public String getAggregateId() { return orderId; }
}
