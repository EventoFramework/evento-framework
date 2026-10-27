package com.evento.test.broker.demo;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** Creates an order with a note: two events from one command (F9). */
public class OpenOrder extends DomainCommand {
    String orderId;
    String note;
    public OpenOrder() {}
    public OpenOrder(String orderId, String note) { this.orderId = orderId; this.note = note; }
    @Override public String getAggregateId() { return orderId; }
}
