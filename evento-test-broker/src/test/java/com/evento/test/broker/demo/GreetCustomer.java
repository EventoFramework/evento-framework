package com.evento.test.broker.demo;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class GreetCustomer extends DomainCommand {
    String customerId;
    public GreetCustomer() {}
    public GreetCustomer(String customerId) { this.customerId = customerId; }
    @Override public String getAggregateId() { return customerId; }
}
