package com.evento.server.es.pg.bench;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;

/** The benchmark order, never snapshotted: its whole story travels with every command. */
@Aggregate(snapshotFrequency = -1)
public class BenchOrderAggregate {

    @AggregateCommandHandler(init = true)
    BenchCreated handle(BenchCreate cmd, BenchState state) {
        return new BenchCreated(cmd.orderId, cmd.region);
    }

    @AggregateCommandHandler
    BenchItemAdded handle(BenchAddItem cmd, BenchState state) {
        if (state == null || state.region == null) {
            throw new IllegalStateException("no such order " + cmd.orderId);
        }
        return new BenchItemAdded(cmd.orderId, cmd.sku, cmd.qty);
    }

    @EventSourcingHandler
    BenchState on(BenchCreated e, BenchState state) {
        var s = state == null ? new BenchState() : state;
        s.region = e.region;
        return s;
    }

    @EventSourcingHandler
    BenchState on(BenchItemAdded e, BenchState state) {
        var s = state == null ? new BenchState() : state;
        s.items++;
        s.qty += e.qty;
        return s;
    }
}
