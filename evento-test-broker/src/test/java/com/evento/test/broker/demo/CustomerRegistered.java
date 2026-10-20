package com.evento.test.broker.demo;

import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.personaldata.DataSubject;
import com.evento.common.personaldata.PersonalData;

public class CustomerRegistered extends DomainEvent {
    @DataSubject String customerId;
    @PersonalData String name;
    public CustomerRegistered() {}
    public CustomerRegistered(String customerId, String name) { this.customerId = customerId; this.name = name; }
    public String customerId() { return customerId; }
    public String name() { return name; }
}
