package com.evento.test.shop;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.messaging.payload.DomainCommand;

public class AddItem extends DomainCommand {
    String orderId;
    String item;
    int price;
    public AddItem() {}
    public AddItem(String orderId, String item, int price) { this.orderId = orderId; this.item = item; this.price = price; }
    @Override public String getAggregateId() { return orderId; }
}
