package com.evento.lab.cluster.query.bundle;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.lab.cluster.api.event.PriceChangedEvent;
import com.evento.lab.cluster.support.store.CatalogFanOut;
import com.evento.lab.cluster.support.store.PriceChange;

/**
 * The global lane: consumes the literal context {@code ALL} and propagates
 * every price change to every regional database ({@code DATA_TARGETS}).
 *
 * <p>It runs an engine only in deployments whose {@code EVENTO_CONTEXTS}
 * names {@code ALL}; every such deployment shares bundle id and consumer-state
 * database, so their engines compete for one lane
 * ({@code order-query_CatalogProjector_1_ALL}): one projects, the others stand
 * by. When a target database is down the handler fails after serving the
 * others and the event is retried (inline retry, forever), which holds the
 * lane for every region: the price of exactly-once per target without a
 * per-target lane. See {@link CatalogFanOut}.
 */
@Projector(version = 1)
public class CatalogProjector {

    private final CatalogFanOut fanOut;

    public CatalogProjector(CatalogFanOut fanOut) {
        this.fanOut = fanOut;
    }

    @EventHandler(retryDelay = 2000)
    void on(PriceChangedEvent e, PublishedEvent pe) throws Exception {
        fanOut.apply(new PriceChange(pe.getEventSequenceNumber(), e.getSku(), e.getPriceCents(), e.getCurrency(),
                e.getContext()));
    }
}
