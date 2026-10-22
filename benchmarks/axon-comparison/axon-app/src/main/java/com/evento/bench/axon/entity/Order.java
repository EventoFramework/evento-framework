package com.evento.bench.axon.entity;

import com.evento.bench.axon.api.Commands.AddItem;
import com.evento.bench.axon.api.Commands.CreateOrder;
import com.evento.bench.axon.api.Commands.ItemAdded;
import com.evento.bench.axon.api.Commands.OrderCreated;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;

/**
 * The benchmark order as an Axon Framework 5 event-sourced entity, with the framework defaults
 * (no snapshots, no cache). Created by a creational (static) command handler and, when sourced,
 * from its first event.
 */
@EventSourced(tagKey = "Order", idType = String.class)
public class Order {

    private int items;

    @EntityCreator
    public Order(OrderCreated event) {
    }

    @CommandHandler
    public static void create(CreateOrder command, EventAppender appender) {
        appender.append(new OrderCreated(command.orderId()));
    }

    @CommandHandler
    public void handle(AddItem command, EventAppender appender) {
        appender.append(new ItemAdded(command.orderId(), command.sku(), command.quantity()));
    }

    @EventSourcingHandler
    public void on(ItemAdded event) {
        items++;
    }
}
