package com.evento.server.bus.spring;

import com.evento.server.bus.lifecycle.BusLifecycle;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Actuator health contribution for the v2 bus. Reports {@code UP} once the Netty transport is
 * bound to a port and accepting connections, exposing the bound port and the current connected /
 * available node counts. Surfaces under {@code /actuator/health} and contributes to the
 * readiness probe so orchestrators don't route traffic before the broker can accept bundles.
 */
public class BusHealthIndicator implements HealthIndicator {

    private final BusLifecycle bus;

    public BusHealthIndicator(BusLifecycle bus) {
        this.bus = bus;
    }

    @Override
    public Health health() {
        int port = bus.boundPort();
        if (port <= 0) {
            return Health.down().withDetail("reason", "transport not bound").build();
        }
        return Health.up()
                .withDetail("boundPort", port)
                .withDetail("connectedNodes", bus.view().size())
                .withDetail("availableNodes", bus.availableView().size())
                .build();
    }
}
