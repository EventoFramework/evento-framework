package com.evento.test.shop;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.messaging.payload.DomainCommand;

public class DeleteOrder extends DomainCommand {
    String orderId;
    public DeleteOrder() {}
    public DeleteOrder(String orderId) { this.orderId = orderId; }
    @Override public String getAggregateId() { return orderId; }
}
