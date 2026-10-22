package com.evento.bench.axon.api;

import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.modelling.annotation.TargetEntityId;

/** The benchmark domain for Axon Framework 5: the same commands and events as the Evento app. */
public final class Commands {

    private Commands() {
    }

    public record CreateOrder(@TargetEntityId String orderId) {
    }

    public record AddItem(@TargetEntityId String orderId, String sku, int quantity) {
    }

    /** Tagged with the order: the aggregate-based storage engines need exactly one tag per event. */
    public record OrderCreated(@EventTag(key = "Order") String orderId) {
    }

    public record ItemAdded(@EventTag(key = "Order") String orderId, String sku, int quantity) {
    }
}
