package com.evento.application.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BundleClientConfigFailbackTest {

    private static BundleClientConfig.Builder base() {
        return BundleClientConfig.builder().bundleId("b").instanceId("i");
    }

    @Test
    void failbackIsOnByDefaultWithSeveralEndpoints() {
        var cfg = base().endpoint("evento-eu", 3030).endpoint("evento-am", 3030).build();

        assertThat(cfg.failbackEnabled()).isTrue();
        assertThat(cfg.failbackActive()).isTrue();
        assertThat(cfg.failbackProbeInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(cfg.failbackStabilityWindow()).isEqualTo(Duration.ofSeconds(30));
        assertThat(cfg.failbackJitter()).isEqualTo(Duration.ofSeconds(10));
        assertThat(cfg.failbackDrainTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void withOneEndpointThereIsNothingToFailBackTo() {
        var cfg = base().host("evento-eu").port(3030).build();

        assertThat(cfg.failbackEnabled()).isTrue();
        assertThat(cfg.failbackActive()).isFalse();
    }

    @Test
    void failbackCanBeTurnedOff() {
        var cfg = base().endpoint("a", 1).endpoint("b", 2).failbackEnabled(false).build();

        assertThat(cfg.failbackActive()).isFalse();
    }

    @Test
    void invalidDurationsFallBackToTheDefaultsAndZeroIsAllowedWhereItMeansSomething() {
        var cfg = base().endpoint("a", 1).endpoint("b", 2)
                .failbackProbeInterval(Duration.ZERO)
                .failbackStabilityWindow(Duration.ofSeconds(-1))
                .failbackJitter(Duration.ZERO)
                .failbackDrainTimeout(Duration.ZERO)
                .build();

        // A zero interval would spin; a negative window is meaningless.
        assertThat(cfg.failbackProbeInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(cfg.failbackStabilityWindow()).isEqualTo(Duration.ofSeconds(30));
        // No jitter and no drain are legitimate choices.
        assertThat(cfg.failbackJitter()).isZero();
        assertThat(cfg.failbackDrainTimeout()).isZero();
    }
}
