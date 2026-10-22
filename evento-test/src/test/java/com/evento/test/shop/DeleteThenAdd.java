package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** A handler bug: an event after the one that deletes the aggregate. */
public class DeleteThenAdd extends DomainCommand {
    String orderId;
    public DeleteThenAdd() {}
    public DeleteThenAdd(String orderId) { this.orderId = orderId; }
    @Override public String getAggregateId() { return orderId; }
}
