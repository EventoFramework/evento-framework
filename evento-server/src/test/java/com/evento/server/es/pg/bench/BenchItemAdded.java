package com.evento.server.es.pg.bench;

import com.evento.common.modeling.messaging.payload.DomainEvent;

/** Payload of the benchmark aggregates ({@code CommandLatencyBenchmarkIT}). */
public class BenchItemAdded extends DomainEvent {
    public String orderId;
    public String sku;
    public int qty;

    public BenchItemAdded() {
    }

    public BenchItemAdded(String orderId, String sku, int qty) {
        this.orderId = orderId;
        this.sku = sku;
        this.qty = qty;
    }
}
