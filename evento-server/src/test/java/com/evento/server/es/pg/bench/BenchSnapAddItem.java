package com.evento.server.es.pg.bench;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** Payload of the benchmark aggregates ({@code CommandLatencyBenchmarkIT}). */
public class BenchSnapAddItem extends DomainCommand {
    public String orderId;
    public String sku;
    public int qty;

    public BenchSnapAddItem() {
    }

    public BenchSnapAddItem(String orderId, String sku, int qty) {
        this.orderId = orderId;
        this.sku = sku;
        this.qty = qty;
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }
}
