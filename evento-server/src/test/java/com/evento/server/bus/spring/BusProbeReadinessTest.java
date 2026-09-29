package com.evento.server.bus.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailabilityBean;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fail-back probe gate of the Spring server (N16 review, MEDIUM-6) follows
 * Spring Boot's readiness: not ready while the context starts, ready once the
 * application accepts traffic, not ready again when it refuses it (shutdown).
 */
class BusProbeReadinessTest {

    @Test
    void probesAreAnsweredOnlyWhileTheApplicationAcceptsTraffic() {
        var availability = new ApplicationAvailabilityBean();
        var ready = BusConfiguration.probeReadiness(availability);

        assertThat(ready.getAsBoolean()).as("before the application is ready").isFalse();

        availability.onApplicationEvent(new AvailabilityChangeEvent<>(this, ReadinessState.ACCEPTING_TRAFFIC));
        assertThat(ready.getAsBoolean()).isTrue();

        availability.onApplicationEvent(new AvailabilityChangeEvent<>(this, ReadinessState.REFUSING_TRAFFIC));
        assertThat(ready.getAsBoolean()).isFalse();
    }

    @Test
    void withoutAnAvailabilityBeanTheBrokerIsAlwaysReady() {
        assertThat(BusConfiguration.probeReadiness(null).getAsBoolean()).isTrue();
    }
}
