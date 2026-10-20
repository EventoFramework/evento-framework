package com.evento.test.broker.demo;

import com.evento.common.modeling.messaging.payload.DomainEvent;

/** What the aggregate knew of the name when greeting: shows the story as the aggregate reads it. */
public class CustomerGreeted extends DomainEvent {
    String customerId;
    String knownName;
    public CustomerGreeted() {}
    public CustomerGreeted(String customerId, String knownName) { this.customerId = customerId; this.knownName = knownName; }
    public String knownName() { return knownName; }
}
