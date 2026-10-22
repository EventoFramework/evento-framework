package com.evento.test.shop;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class Notify extends ServiceCommand {
    String orderId;
    public Notify() {}
    public Notify(String orderId) { this.orderId = orderId; }
}
