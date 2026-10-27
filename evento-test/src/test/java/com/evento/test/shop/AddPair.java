package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** Two items in one command: two events (F9). */
public class AddPair extends DomainCommand {
    String orderId;
    String first;
    String second;
    int price;
    public AddPair() {}
    public AddPair(String orderId, String first, String second, int price) {
        this.orderId = orderId; this.first = first; this.second = second; this.price = price;
    }
    @Override public String getAggregateId() { return orderId; }
}
