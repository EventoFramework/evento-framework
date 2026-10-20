package com.evento.test.broker.demo;

import com.evento.common.modeling.messaging.payload.View;

public class OrderCount implements View {
    int count;
    public OrderCount() {}
    public OrderCount(int count) { this.count = count; }
    public int count() { return count; }
}
