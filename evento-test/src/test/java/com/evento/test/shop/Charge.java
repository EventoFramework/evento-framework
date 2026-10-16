package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class Charge extends ServiceCommand {
    String orderId;
    int amount;
    public Charge() {}
    public Charge(String orderId, int amount) { this.orderId = orderId; this.amount = amount; }
    public int amount() { return amount; }
}
