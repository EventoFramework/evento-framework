package com.evento.server.es.pg.bench;

import com.evento.common.modeling.messaging.payload.DomainEvent;

/** Payload of the benchmark aggregates ({@code CommandLatencyBenchmarkIT}). */
public class BenchCreated extends DomainEvent {
    public String orderId;
    public String region;

    public BenchCreated() {
    }

    public BenchCreated(String orderId, String region) {
        this.orderId = orderId;
        this.region = region;
    }
}
