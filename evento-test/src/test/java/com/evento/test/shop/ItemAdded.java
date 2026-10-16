package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ItemAdded extends DomainEvent {
    String orderId;
    String item;
    int price;
    public ItemAdded() {}
    public ItemAdded(String orderId, String item, int price) { this.orderId = orderId; this.item = item; this.price = price; }
    public String item() { return item; }
}
