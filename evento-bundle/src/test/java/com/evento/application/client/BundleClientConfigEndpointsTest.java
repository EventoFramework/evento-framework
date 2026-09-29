package com.evento.application.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BundleClientConfigEndpointsTest {

    private static BundleClientConfig.Builder base() {
        return BundleClientConfig.builder().bundleId("b").instanceId("i");
    }

    @Test
    void theSingleHostAndPortStillWorkAsAOneEndpointList() {
        var cfg = base().host("h").port(3030).build();

        assertThat(cfg.endpoints()).containsExactly(new BundleClientConfig.Endpoint("h", 3030));
        assertThat(cfg.host()).isEqualTo("h");
        assertThat(cfg.port()).isEqualTo(3030);
    }

    @Test
    void anEndpointListKeepsItsOrderAndHostPortMirrorTheFirst() {
        var cfg = base()
                .endpoint("evento-eu", 3030)
                .endpoint("evento-am", 3031)
                .endpoint("evento-apac", 3032)
                .build();

        assertThat(cfg.endpoints()).extracting(BundleClientConfig.Endpoint::host)
                .containsExactly("evento-eu", "evento-am", "evento-apac");
        assertThat(cfg.host()).isEqualTo("evento-eu");
        assertThat(cfg.port()).isEqualTo(3030);
    }

    @Test
    void theListWinsOverTheSingleHost() {
        var cfg = base().host("ignored").port(1)
                .endpoints(List.of(new BundleClientConfig.Endpoint("a", 2)))
                .build();

        assertThat(cfg.endpoints()).containsExactly(new BundleClientConfig.Endpoint("a", 2));
    }

    @Test
    void noEndpointAtAllIsRejected() {
        assertThatThrownBy(() -> base().build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankIdentityExpectationsMeanNone() {
        var cfg = base().host("h").port(1)
                .expectedClusterName(" ")
                .expectedServerInstanceId("")
                .build();

        assertThat(cfg.expectedClusterName()).isNull();
        assertThat(cfg.expectedServerInstanceId()).isNull();
    }
}
