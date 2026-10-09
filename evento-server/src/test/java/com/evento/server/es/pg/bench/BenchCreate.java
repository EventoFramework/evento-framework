package com.evento.server.es.pg.bench;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** Payload of the benchmark aggregates ({@code CommandLatencyBenchmarkIT}). */
public class BenchCreate extends DomainCommand {
    public String orderId;
    public String region;

    public BenchCreate() {
    }

    public BenchCreate(String orderId, String region) {
        this.orderId = orderId;
        this.region = region;
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }
}
