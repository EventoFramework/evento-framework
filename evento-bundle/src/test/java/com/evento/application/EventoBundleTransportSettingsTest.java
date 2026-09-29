package com.evento.application;

import com.evento.transport.netty.NettyTransportConfig;
import com.evento.transport.reconnect.ExponentialBackoffWithJitter;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The link-level knobs reachable from {@link EventoBundle.Builder}. */
class EventoBundleTransportSettingsTest {

    @Test
    void untouchedBuilderUsesTheTransportDefaults() {
        var cfg = EventoBundle.Builder.builder().effectiveTransportConfig();
        var defaults = NettyTransportConfig.defaults();

        assertThat(cfg.heartbeatReadIdle()).isEqualTo(defaults.heartbeatReadIdle());
        assertThat(cfg.heartbeatWriteIdle()).isEqualTo(defaults.heartbeatWriteIdle());
    }

    @Test
    void aShortReadIdleAlsoShortensThePingCadenceSoTheLinkStaysUp() {
        var cfg = EventoBundle.Builder.builder()
                .setHeartbeatReadIdle(Duration.ofSeconds(9))
                .effectiveTransportConfig();

        assertThat(cfg.heartbeatReadIdle()).isEqualTo(Duration.ofSeconds(9));
        assertThat(cfg.heartbeatWriteIdle()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void aReadIdleLongerThanTheCadenceKeepsTheCadence() {
        var cfg = EventoBundle.Builder.builder()
                .setHeartbeatReadIdle(Duration.ofSeconds(60))
                .effectiveTransportConfig();

        assertThat(cfg.heartbeatWriteIdle()).isEqualTo(NettyTransportConfig.defaults().heartbeatWriteIdle());
    }

    @Test
    void reconnectStrategyAndExplicitTransportConfigCompose() {
        var strategy = new ExponentialBackoffWithJitter(Duration.ofMillis(10), Duration.ofMillis(20), 0, 3);
        var base = NettyTransportConfig.defaults().withHeartbeat(Duration.ofSeconds(1), Duration.ofSeconds(4));
        var builder = EventoBundle.Builder.builder()
                .setTransportConfig(base)
                .setReconnectStrategy(strategy)
                .setDefaultRequestTimeout(Duration.ofSeconds(7))
                .setMaxInFlightRequests(16);

        var cfg = builder.effectiveTransportConfig();

        assertThat(cfg.reconnectStrategy()).isSameAs(strategy);
        assertThat(cfg.heartbeatReadIdle()).isEqualTo(Duration.ofSeconds(4));
        assertThat(builder.getDefaultRequestTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(builder.getMaxInFlightRequests()).isEqualTo(16);
    }
}
