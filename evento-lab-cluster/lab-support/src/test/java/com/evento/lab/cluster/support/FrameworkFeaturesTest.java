package com.evento.lab.cluster.support;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shim must behave the same whether the framework has a feature or not:
 * apply it when present, record "unavailable" when absent, never fail the
 * bundle start over it. The real framework types are exercised too, so this
 * test passes on the framework before and after the cluster fixes.
 */
class FrameworkFeaturesTest {

    public static final class WithZone {
        String zone;

        public WithZone setZone(String zone) {
            this.zone = zone;
            return this;
        }

        public void setBroken(String v) {
            throw new IllegalStateException("bad value " + v);
        }
    }

    public static final class WithoutZone {
    }

    @Test
    void invokesAMethodThatExists() {
        var target = new WithZone();
        assertThat(FrameworkFeatures.invoke(target, "setZone", String.class, "EU")).isTrue();
        assertThat(target.zone).isEqualTo("EU");
    }

    @Test
    void reportsAMissingMethodInsteadOfFailing() {
        assertThat(FrameworkFeatures.invoke(new WithoutZone(), "setZone", String.class, "EU")).isFalse();
        assertThat(FrameworkFeatures.call(new WithoutZone(), "withExpectedClusterName", String.class, "x")).isEmpty();
    }

    @Test
    void aFailureInsideAnExistingFeatureIsNotHidden() {
        assertThatThrownBy(() -> FrameworkFeatures.invoke(new WithZone(), "setBroken", String.class, "x"))
                .hasMessageContaining("bad value x");
    }

    @Test
    void worksOnTheRealFrameworkTypesWhateverTheirVersion() {
        var features = new FrameworkFeatures();
        var builder = EventoBundle.Builder.builder();
        features.zone(builder, "EU");
        features.heartbeatReadIdle(builder, Duration.ofSeconds(10));
        var config = new EventoServerMessageBusConfiguration(new ClusterNodeAddress("a", 3030),
                new ClusterNodeAddress("b", 3030));
        var withName = features.expectedClusterName(config, "evento-lab-cluster");
        assertThat(withName).isNotNull();
        assertThat(withName.getAddresses()).hasSize(2);
        features.endpointFailover(2);
        assertThat(features.outcomes()).extracting(FrameworkFeatures.Outcome::feature)
                .containsExactly("zone", "heartbeatReadIdle", "expectedClusterName", "endpointFailover");
        // nothing requested, nothing recorded
        features.zone(builder, null);
        assertThat(features.expectedClusterName(config, null)).isSameAs(config);
        assertThat(features.outcomes()).hasSize(4);
    }

    @Test
    void failbackIsAlwaysRecordedAndTheWindowAppliedWhenTheFrameworkHasIt() {
        var features = new FrameworkFeatures();
        var builder = EventoBundle.Builder.builder();

        features.failback(builder, null);
        var withDefault = features.outcomes().getFirst();
        assertThat(withDefault.feature()).isEqualTo("failback");
        assertThat(withDefault.value()).isEqualTo("default window");

        features.failback(builder, Duration.ofSeconds(20));
        var withWindow = features.outcomes().getFirst();
        assertThat(features.outcomes()).hasSize(1);
        assertThat(withWindow.available()).isEqualTo(withDefault.available());
        assertThat(withWindow.value()).isEqualTo("PT20S");
    }
}
