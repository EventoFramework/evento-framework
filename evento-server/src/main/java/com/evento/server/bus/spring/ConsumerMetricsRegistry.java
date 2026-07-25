package com.evento.server.bus.spring;

import com.evento.common.modeling.messaging.message.internal.consumer.ConsumerStatsMessage;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exposes the parallel-consumption counters bundles push to the server as Micrometer meters.
 *
 * <p>The numbers originate in the bundles — executor permits and per-consumer trackers are
 * bundle-side objects — and arrive on the admin notification channel as
 * {@link ConsumerStatsMessage}. This registry holds the latest snapshot per node and binds a
 * meter per series to it, so a Prometheus scrape reads memory rather than fanning out
 * requests across the cluster.
 *
 * <h2>Meters</h2>
 * <ul>
 *   <li>{@code evento.consumer.executor.capacity} / {@code .in.flight} — gauges, tagged
 *       {@code bundle,instance,executor}</li>
 *   <li>{@code evento.consumer.executor.admitted} / {@code .rejected} / {@code .completed} /
 *       {@code .failed} — counters. <b>{@code rejected} is the one to alert on:</b> it is the
 *       executor reporting that it is the bottleneck.</li>
 *   <li>{@code evento.consumer.async.in.flight} — gauge, tagged {@code bundle,instance,consumer,component}</li>
 *   <li>{@code evento.consumer.async.submit.timeouts} / {@code .transient.failures} — counters</li>
 * </ul>
 *
 * <p>Meters are removed when a node leaves, so a rolling restart does not accumulate series
 * for instances that no longer exist — the usual way this kind of gauge turns into a
 * cardinality leak.
 */
@Component
public class ConsumerMetricsRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConsumerMetricsRegistry.class);

    private final MeterRegistry registry;

    /** instanceId → live series, so a node departure can drop exactly its meters. */
    private final Map<String, NodeMeters> byInstance = new ConcurrentHashMap<>();

    public ConsumerMetricsRegistry(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Values a meter reads. Held strongly here — Micrometer gauges keep only a weak reference. */
    private static final class Series {
        final AtomicLong capacity = new AtomicLong();
        final AtomicLong inFlight = new AtomicLong();
        final AtomicLong admitted = new AtomicLong();
        final AtomicLong rejected = new AtomicLong();
        final AtomicLong completed = new AtomicLong();
        final AtomicLong failed = new AtomicLong();
        final AtomicLong submitTimeouts = new AtomicLong();
        final AtomicLong transientFailures = new AtomicLong();
    }

    private static final class NodeMeters {
        final Map<String, Series> executors = new ConcurrentHashMap<>();
        final Map<String, Series> consumers = new ConcurrentHashMap<>();
        final List<Meter.Id> meterIds = new ArrayList<>();
    }

    public void update(String bundleId, String instanceId, ConsumerStatsMessage stats) {
        if (bundleId == null || instanceId == null || stats == null) return;
        var node = byInstance.computeIfAbsent(instanceId, k -> new NodeMeters());

        for (var e : stats.getExecutors()) {
            if (e.getName() == null) continue;
            var series = node.executors.computeIfAbsent(e.getName(),
                    name -> registerExecutor(node, bundleId, instanceId, name));
            series.capacity.set(e.getCapacity());
            series.inFlight.set(e.getInFlight());
            series.admitted.set(e.getAdmitted());
            series.rejected.set(e.getRejected());
            series.completed.set(e.getCompleted());
            series.failed.set(e.getFailed());
        }

        for (var c : stats.getConsumers()) {
            if (c.getConsumerId() == null) continue;
            var series = node.consumers.computeIfAbsent(c.getConsumerId(),
                    id -> registerConsumer(node, bundleId, instanceId, id,
                            c.getComponentName() == null ? "unknown" : c.getComponentName()));
            series.inFlight.set(c.getInFlight());
            series.submitTimeouts.set(c.getSubmitTimeouts());
            series.transientFailures.set(c.getTransientFailures());
        }
    }

    private Series registerExecutor(NodeMeters node, String bundleId, String instanceId, String name) {
        var s = new Series();
        var tags = new String[]{"bundle", bundleId, "instance", instanceId, "executor", name};
        track(node, Gauge.builder("evento.consumer.executor.capacity", s, x -> x.capacity.get())
                .description("Configured concurrency of the consumer executor")
                .tags(tags).register(registry));
        track(node, Gauge.builder("evento.consumer.executor.in.flight", s, x -> x.inFlight.get())
                .description("Handler tasks currently running on the executor")
                .tags(tags).register(registry));
        track(node, FunctionCounter.builder("evento.consumer.executor.admitted", s, x -> x.admitted.get())
                .description("Tasks granted a permit")
                .tags(tags).register(registry));
        track(node, FunctionCounter.builder("evento.consumer.executor.rejected", s, x -> x.rejected.get())
                .description("Submissions refused for lack of capacity — the backpressure signal")
                .tags(tags).register(registry));
        track(node, FunctionCounter.builder("evento.consumer.executor.completed", s, x -> x.completed.get())
                .description("Tasks that finished")
                .tags(tags).register(registry));
        track(node, FunctionCounter.builder("evento.consumer.executor.failed", s, x -> x.failed.get())
                .description("Tasks that threw past the consumer's own wrapper")
                .tags(tags).register(registry));
        return s;
    }

    private Series registerConsumer(NodeMeters node, String bundleId, String instanceId,
                                    String consumerId, String componentName) {
        var s = new Series();
        var tags = new String[]{"bundle", bundleId, "instance", instanceId,
                "consumer", consumerId, "component", componentName};
        track(node, Gauge.builder("evento.consumer.async.in.flight", s, x -> x.inFlight.get())
                .description("Async handler tasks this consumer has dispatched and not yet finished")
                .tags(tags).register(registry));
        track(node, FunctionCounter.builder("evento.consumer.async.submit.timeouts", s,
                        x -> x.submitTimeouts.get())
                .description("Cycles ended early because the executor had no capacity")
                .tags(tags).register(registry));
        track(node, FunctionCounter.builder("evento.consumer.async.transient.failures", s,
                        x -> x.transientFailures.get())
                .description("Transient failures in async handlers — these dead-letter, they do not redeliver")
                .tags(tags).register(registry));
        return s;
    }

    private void track(NodeMeters node, Meter meter) {
        synchronized (node.meterIds) {
            node.meterIds.add(meter.getId());
        }
    }

    /**
     * Drop every meter belonging to a departed node. Without this a rolling restart leaves a
     * series per dead instance behind for ever.
     */
    public void onNodeLeft(String instanceId) {
        var node = byInstance.remove(instanceId);
        if (node == null) return;
        synchronized (node.meterIds) {
            for (var id : node.meterIds) registry.remove(id);
        }
        log.debug("event=consumer_metrics_removed instance={} meters={}", instanceId, node.meterIds.size());
    }

    /** Visible for tests. */
    public int trackedInstances() {
        return byInstance.size();
    }
}
