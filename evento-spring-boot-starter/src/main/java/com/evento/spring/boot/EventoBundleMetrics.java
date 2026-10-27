package com.evento.spring.boot;

import com.evento.application.EventoBundle;
import com.evento.application.client.BundleClientState;
import com.evento.application.client.metrics.LatencyHistogram;
import com.evento.application.client.metrics.LatencyRecorder;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Publishes what the bundle measures itself to the application's {@link MeterRegistry}:
 *
 * <ul>
 *   <li>{@code evento.bundle.connected}: 1 while the connection to the server is {@code READY};</li>
 *   <li>{@code evento.bundle.request{payload}}: round trip of the requests this bundle sends
 *       (count, total, and {@code evento.bundle.request.p99} / {@code .max});</li>
 *   <li>{@code evento.bundle.handler{type}}: time its handlers ran ({@code aggregate},
 *       {@code service}, {@code query}, and their {@code .decode} / {@code .encode}).</li>
 * </ul>
 *
 * <p>All tagged {@code bundle} and {@code instance}. Payload types appear as they are first
 * measured: new ones are picked up every {@code refresh}.
 */
public class EventoBundleMetrics implements ApplicationListener<EventoBundleStartedEvent>, DisposableBean {

    private final MeterRegistry registry;
    private final Duration refresh;
    private final Set<String> registered = ConcurrentHashMap.newKeySet();
    private volatile ScheduledExecutorService scheduler;

    public EventoBundleMetrics(MeterRegistry registry, Duration refresh) {
        this.registry = registry;
        this.refresh = refresh;
    }

    @Override
    public void onApplicationEvent(EventoBundleStartedEvent event) {
        var bundle = event.getBundle();
        var tags = Tags.of("bundle", bundle.getBundleId(), "instance", bundle.getInstanceId());
        Gauge.builder("evento.bundle.connected", bundle,
                        b -> b.getConnectionState() == BundleClientState.READY ? 1 : 0)
                .description("1 while the bundle's connection to the server is READY")
                .tags(tags)
                .register(registry);
        if (bundle.getInboundNotExecuted() != null) {
            io.micrometer.core.instrument.FunctionCounter.builder("evento.bundle.inbound.not.executed", bundle,
                            b -> b.getInboundNotExecuted()[0])
                    .description("Requests answered without running their handler: the caller's deadline had passed")
                    .tags(tags).tag("reason", "expired")
                    .register(registry);
            io.micrometer.core.instrument.FunctionCounter.builder("evento.bundle.inbound.not.executed", bundle,
                            b -> b.getInboundNotExecuted()[1])
                    .description("Requests refused because the bundle was handling evento.bundle.max-inbound-requests")
                    .tags(tags).tag("reason", "busy")
                    .register(registry);
        }
        if (bundle.getLatencies() == null) return;
        bindNew(bundle, tags);
        var s = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "evento-bundle-metrics");
            t.setDaemon(true);
            return t;
        });
        s.scheduleWithFixedDelay(() -> bindNew(bundle, tags), refresh.toMillis(), refresh.toMillis(),
                TimeUnit.MILLISECONDS);
        scheduler = s;
    }

    void bindNew(EventoBundle bundle, Tags tags) {
        var latencies = bundle.getLatencies();
        bind(latencies.requestRtt(), "evento.bundle.request", "payload", tags,
                "Round trip of the requests this bundle sent");
        bind(latencies.handlers(), "evento.bundle.handler", "type", tags,
                "Time this bundle's handlers ran");
    }

    private void bind(LatencyRecorder recorder, String name, String tag, Tags tags, String description) {
        for (var key : recorder.snapshot().keySet()) {
            if (!registered.add(name + "|" + key)) continue;
            LatencyHistogram h = recorder.histogram(key);
            var t = tags.and(tag, key);
            FunctionTimer.builder(name, h, LatencyHistogram::count, LatencyHistogram::totalNanos, TimeUnit.NANOSECONDS)
                    .description(description).tags(t).register(registry);
            TimeGauge.builder(name + ".p99", h, TimeUnit.NANOSECONDS, x -> x.percentileNanos(0.99))
                    .tags(t).register(registry);
            TimeGauge.builder(name + ".max", h, TimeUnit.NANOSECONDS, LatencyHistogram::maxNanos)
                    .tags(t).register(registry);
        }
    }

    @Override
    public void destroy() {
        var s = scheduler;
        if (s != null) s.shutdownNow();
    }
}
