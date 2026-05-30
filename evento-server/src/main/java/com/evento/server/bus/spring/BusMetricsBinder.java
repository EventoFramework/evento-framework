package com.evento.server.bus.spring;

import com.evento.server.bus.correlation.CorrelationStore;
import com.evento.server.bus.lifecycle.BusLifecycle;
import com.evento.server.bus.router.ForwardingTable;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Binds the v2 bus's internal state to Micrometer. Spring Boot applies every {@link MeterBinder}
 * bean to all registries (Prometheus, SimpleMeterRegistry, …), so these meters appear on
 * {@code /actuator/prometheus} and {@code /actuator/metrics} without further wiring.
 *
 * <p>Meters (all polled from already-existing, thread-safe accessors — no hot-path cost):
 * <ul>
 *   <li>{@code evento.server.connections} — connected bundle nodes</li>
 *   <li>{@code evento.server.connections.available} — nodes currently able to receive</li>
 *   <li>{@code evento.server.correlations.pending} — outstanding server-initiated requests</li>
 *   <li>{@code evento.server.forwarding.table.size} — in-flight relayed requests</li>
 *   <li>{@code evento.server.forwarded} (tag {@code path=raw|reencoded}) — zero-copy vs re-encoded forwards</li>
 * </ul>
 */
public class BusMetricsBinder implements MeterBinder {

    private final BusLifecycle bus;
    private final CorrelationStore correlations;
    private final ForwardingTable forwarding;

    public BusMetricsBinder(BusLifecycle bus, CorrelationStore correlations, ForwardingTable forwarding) {
        this.bus = bus;
        this.correlations = correlations;
        this.forwarding = forwarding;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("evento.server.connections", bus, b -> b.view().size())
                .description("Connected bundle nodes")
                .register(registry);
        Gauge.builder("evento.server.connections.available", bus, b -> b.availableView().size())
                .description("Bundle nodes currently able to receive messages")
                .register(registry);
        Gauge.builder("evento.server.correlations.pending", correlations, CorrelationStore::pendingCount)
                .description("Outstanding server-initiated correlations awaiting a response")
                .register(registry);
        Gauge.builder("evento.server.forwarding.table.size", forwarding, ForwardingTable::size)
                .description("In-flight bundle-to-bundle requests being relayed")
                .register(registry);
        FunctionCounter.builder("evento.server.forwarded", bus, BusLifecycle::forwardedRawCount)
                .description("Requests/responses relayed between bundles")
                .tag("path", "raw")
                .register(registry);
        FunctionCounter.builder("evento.server.forwarded", bus, BusLifecycle::forwardedReencodedCount)
                .description("Requests/responses relayed between bundles")
                .tag("path", "reencoded")
                .register(registry);
    }
}
