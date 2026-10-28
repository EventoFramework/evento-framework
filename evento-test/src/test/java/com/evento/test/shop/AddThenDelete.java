package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** A handler bug: an event its handler does not declare in {@code produces}. */
public class AddThenDelete extends DomainCommand {
    String orderId;
    public AddThenDelete() {}
    public AddThenDelete(String orderId) { this.orderId = orderId; }
    @Override public String getAggregateId() { return orderId; }
}
