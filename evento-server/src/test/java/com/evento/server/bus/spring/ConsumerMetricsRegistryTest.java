package com.evento.server.bus.spring;

import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerStatsMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Bundle-pushed consumer counters becoming Micrometer meters, and — just as importantly —
 * going away again when the node does.
 */
class ConsumerMetricsRegistryTest {

    private SimpleMeterRegistry meters;
    private ConsumerMetricsRegistry registry;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        registry = new ConsumerMetricsRegistry(meters);
    }

    private static ConsumerStatsMessage stats(String executorName, long rejected, int inFlight) {
        var e = new ConsumerStatsMessage.ExecutorStats();
        e.setName(executorName);
        e.setCapacity(64);
        e.setInFlight(inFlight);
        e.setAdmitted(1000);
        e.setRejected(rejected);
        e.setCompleted(990);
        e.setFailed(0);

        var c = new ConsumerStatsMessage.ConsumerStats();
        c.setConsumerId("orders_OrderProjector_1_ctx");
        c.setComponentName("OrderProjector");
        c.setInFlight(inFlight);
        c.setSubmitTimeouts(7);
        c.setTransientFailures(3);

        var msg = new ConsumerStatsMessage();
        msg.setExecutors(List.of(e));
        msg.setConsumers(List.of(c));
        return msg;
    }

    private double value(String name) {
        var meter = meters.find(name).meter();
        assertThat(meter).as("meter %s should exist", name).isNotNull();
        return meter.measure().iterator().next().getValue();
    }

    @Test
    void publishesExecutorAndConsumerSeries() {
        registry.update("orders", "orders-1", stats("read-model", 12, 5));

        assertThat(value("evento.consumer.executor.capacity")).isEqualTo(64);
        assertThat(value("evento.consumer.executor.in.flight")).isEqualTo(5);
        assertThat(value("evento.consumer.executor.admitted")).isEqualTo(1000);
        // The backpressure signal operators alert on.
        assertThat(value("evento.consumer.executor.rejected")).isEqualTo(12);
        assertThat(value("evento.consumer.async.in.flight")).isEqualTo(5);
        assertThat(value("evento.consumer.async.submit.timeouts")).isEqualTo(7);
        assertThat(value("evento.consumer.async.transient.failures")).isEqualTo(3);
    }

    @Test
    void seriesAreTaggedByBundleInstanceAndExecutor() {
        registry.update("orders", "orders-1", stats("read-model", 1, 1));

        var meter = meters.find("evento.consumer.executor.rejected").meter();
        assertThat(meter).isNotNull();
        var tags = meter.getId().getTags();
        assertThat(tags.stream().map(t -> t.getKey() + "=" + t.getValue()))
                .contains("bundle=orders", "instance=orders-1", "executor=read-model");
    }

    @Test
    void laterPushesUpdateInPlaceRatherThanDuplicatingSeries() {
        registry.update("orders", "orders-1", stats("read-model", 1, 1));
        registry.update("orders", "orders-1", stats("read-model", 99, 4));

        assertThat(meters.find("evento.consumer.executor.rejected").meters()).hasSize(1);
        assertThat(value("evento.consumer.executor.rejected")).isEqualTo(99);
        assertThat(value("evento.consumer.executor.in.flight")).isEqualTo(4);
    }

    @Test
    void twoInstancesOfTheSameBundleKeepSeparateSeries() {
        registry.update("orders", "orders-1", stats("read-model", 1, 1));
        registry.update("orders", "orders-2", stats("read-model", 2, 2));

        assertThat(meters.find("evento.consumer.executor.rejected").meters()).hasSize(2);
        assertThat(registry.trackedInstances()).isEqualTo(2);
    }

    @Test
    void aDepartedNodeLosesItsMetersSoRollingRestartsDoNotLeakSeries() {
        registry.update("orders", "orders-1", stats("read-model", 1, 1));
        registry.update("orders", "orders-2", stats("read-model", 2, 2));

        registry.onNodeLeft("orders-1");

        // Only the surviving instance's series remain.
        assertThat(meters.find("evento.consumer.executor.rejected").meters()).hasSize(1);
        assertThat(meters.find("evento.consumer.async.in.flight").meters()).hasSize(1);
        assertThat(registry.trackedInstances()).isEqualTo(1);

        var survivor = meters.find("evento.consumer.executor.rejected").meter();
        assertThat(survivor.getId().getTag("instance")).isEqualTo("orders-2");
    }

    @Test
    void aNodeThatNeverReportedIsSafeToRemove() {
        assertThatCode(() -> registry.onNodeLeft("never-seen")).doesNotThrowAnyException();
    }

    @Test
    void malformedPushesAreIgnoredRatherThanFailing() {
        assertThatCode(() -> {
            registry.update(null, "orders-1", stats("read-model", 1, 1));
            registry.update("orders", null, stats("read-model", 1, 1));
            registry.update("orders", "orders-1", null);
            // An entry with no name cannot be tagged; skip it rather than register a
            // series called "null".
            var nameless = new ConsumerStatsMessage();
            nameless.setExecutors(List.of(new ConsumerStatsMessage.ExecutorStats()));
            nameless.setConsumers(List.of(new ConsumerStatsMessage.ConsumerStats()));
            registry.update("orders", "orders-1", nameless);
        }).doesNotThrowAnyException();

        assertThat(meters.find("evento.consumer.executor.rejected").meters()).isEmpty();
    }
}
