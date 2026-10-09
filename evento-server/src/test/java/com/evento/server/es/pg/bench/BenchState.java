package com.evento.server.es.pg.bench;

import com.evento.common.modeling.state.AggregateState;

/** Payload of the benchmark aggregates ({@code CommandLatencyBenchmarkIT}). */
public class BenchState extends AggregateState {
    public String region;
    public int items;
    public int qty;

    public BenchState() {
    }
}
