package com.evento.application.client;

import com.evento.application.client.handshake.HelloFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The zone a bundle declares travels as one {@code zone:<ZONE>} capability in
 * its Hello — no new wire field — and leaves every other capability alone.
 */
class BundleClientConfigZoneTest {

    private BundleClientConfig.Builder base() {
        return BundleClientConfig.builder()
                .host("127.0.0.1").port(1)
                .bundleId("b").instanceId("b-1");
    }

    @Test
    void noZoneAdvertisesNothing() {
        var cfg = base().capabilities(Set.of("lz4")).build();
        assertThat(cfg.zone()).isNull();
        assertThat(HelloFactory.build(cfg).capabilities()).containsExactlyInAnyOrder("lz4", "stream:1", "revision:1");
    }

    @Test
    void theZoneIsAddedToTheHelloCapabilities() {
        var cfg = base().capabilities(Set.of("lz4")).zone("AM").build();
        assertThat(cfg.zone()).isEqualTo("AM");
        assertThat(HelloFactory.build(cfg).capabilities()).containsExactlyInAnyOrder("lz4", "zone:AM", "stream:1", "revision:1");
    }

    @Test
    void anExplicitZoneReplacesOnePassedAsARawCapabilityWhateverTheCallOrder() {
        var zoneFirst = base().zone(" EU ").capabilities(Set.of("zone:AM", "ping-pong")).build();
        assertThat(zoneFirst.capabilities()).containsExactlyInAnyOrder("zone:EU", "ping-pong", "stream:1", "revision:1");

        var rawOnly = base().capabilities(Set.of("zone:APAC")).build();
        assertThat(rawOnly.zone()).isEqualTo("APAC");
    }

    @Test
    void aBlankZoneIsNone() {
        var cfg = base().zone("  ").build();
        assertThat(cfg.zone()).isNull();
        // No zone capability; only those every 3.0 bundle offers (a Set: no order).
        assertThat(cfg.capabilities()).containsExactlyInAnyOrder("stream:1", "revision:1");
    }
}
