package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class OrderCharged extends ServiceEvent {
    String orderId;
    int amount;
    public OrderCharged() {}
    public OrderCharged(String orderId, int amount) { this.orderId = orderId; this.amount = amount; }
}
