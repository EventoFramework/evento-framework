package com.evento.test.broker.demo;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;

@Aggregate
public class CustomerAggregate {
    @AggregateCommandHandler(init = true)
    CustomerRegistered handle(RegisterCustomer c) {
        return new CustomerRegistered(c.customerId, c.name);
    }

    @EventSourcingHandler
    CustomerState on(CustomerRegistered e) {
        var s = new CustomerState();
        s.name = e.name();
        return s;
    }

    @AggregateCommandHandler
    CustomerGreeted handle(GreetCustomer c, CustomerState state) {
        return new CustomerGreeted(c.customerId, state.name);
    }

    @EventSourcingHandler
    void on(CustomerGreeted e, CustomerState state) {
    }
}
