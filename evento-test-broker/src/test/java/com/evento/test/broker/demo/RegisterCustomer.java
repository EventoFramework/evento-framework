package com.evento.test.broker.demo;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class RegisterCustomer extends DomainCommand {
    String customerId;
    String name;
    public RegisterCustomer() {}
    public RegisterCustomer(String customerId, String name) { this.customerId = customerId; this.name = name; }
    @Override public String getAggregateId() { return customerId; }
}
