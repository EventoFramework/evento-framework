package com.evento.server.bus.spring;

import com.evento.common.modeling.bundle.types.ComponentType;
import com.evento.common.modeling.bundle.types.HandlerType;
import com.evento.server.bus.event.BusEvent;
import com.evento.server.bus.lifecycle.BusLifecycle;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * How long the broker takes to answer each kind of request (O3), from the moment its frame
 * reached this server to the moment the answer left it: {@code evento.server.request}, tagged
 *
 * <ul>
 *   <li>{@code kind}: {@code query}, {@code service_command}, {@code aggregate_command},
 *       {@code invocation}, {@code other} (event fetches, admin requests), learnt from the
 *       handlers the bundles declare;</li>
 *   <li>{@code payload}: the request's payload type, the class name the application chose;</li>
 *   <li>{@code outcome}: {@code ok}, or {@code error} for an error answer (a refusal by the
 *       handler, an expired request, a failed forward).</li>
 * </ul>
 *
 * <p>For a query this is the whole round trip through the broker: dispatch, the handler bundle's
 * work and the network to it and back. For an aggregate command it is the {@code total} of
 * {@code evento.server.command.phase}, which also breaks it down. Queries a bundle answers itself
 * (local short-circuit) never reach the broker; the bundle's {@code evento.bundle.request} times
 * those. Buckets are fixed (1 ms to 30 s, 15 of them) rather than a full percentile histogram: one
 * series set per payload type has to stay affordable with many of them.
 */
@Component
public class RequestMetrics {

    public static final String METRIC = "evento.server.request";

    private static final Duration[] BUCKETS = {
            Duration.ofMillis(1), Duration.ofMillis(2), Duration.ofMillis(5), Duration.ofMillis(10),
            Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250),
            Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(2500), Duration.ofSeconds(5),
            Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(30)};

    private final MeterRegistry registry;
    /** payload type → kind, from the handlers the bundles declared. */
    private final Map<String, String> kinds = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();

    public RequestMetrics(BusLifecycle bus, MeterRegistry registry) {
        this.registry = registry;
        bus.subscribe(event -> {
            if (!(event instanceof BusEvent.BundleDiscovered discovered)) return;
            for (var handler : discovered.discovery().handlers()) {
                String kind = kindOf(handler.getHandlerType(), handler.getComponentType());
                if (kind != null) kinds.put(handler.getHandledPayload(), kind);
            }
        });
        bus.setRequestObserver(this::answered);
    }

    static String kindOf(HandlerType type, ComponentType component) {
        if (type == null) return null;
        return switch (type) {
            case AggregateCommandHandler -> "aggregate_command";
            case CommandHandler -> component == ComponentType.Service ? "service_command" : null;
            case QueryHandler -> "query";
            case InvocationHandler -> "invocation";
            default -> null; // event handlers are not requests
        };
    }

    void answered(String payloadType, long nanos, boolean ok) {
        if (payloadType == null) return;
        String kind = kinds.getOrDefault(payloadType, "other");
        String outcome = ok ? "ok" : "error";
        timers.computeIfAbsent(kind + '\u0000' + payloadType + '\u0000' + outcome, k -> Timer.builder(METRIC)
                        .description("Time from a request reaching this broker to its answer leaving it")
                        .tag("kind", kind)
                        .tag("payload", payloadType)
                        .tag("outcome", outcome)
                        .serviceLevelObjectives(BUCKETS)
                        .register(registry))
                .record(nanos, TimeUnit.NANOSECONDS);
    }
}
