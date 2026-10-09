package com.evento.lab.cluster.support.web;

import com.evento.lab.cluster.support.BundleRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Latencies measured inside this JVM, for the command-latency breakdown
 * ({@code docs/command-latency.md}):
 * <ul>
 *   <li>{@code requestRtt}: the bundle's own request round trips by payload type
 *       ({@code EventoBundle.getLatencies().requestRtt()}; frame out → answer frame in);</li>
 *   <li>{@code handlers}: the bundle's handlers' execution time by handler type
 *       ({@code aggregate}, {@code service}, {@code query}, plus {@code .decode} / {@code .encode});</li>
 *   <li>{@code operations}: what this lab code timed itself (the gateway: {@code send} per HTTP
 *       operation, Evento encode/decode included).</li>
 * </ul>
 * Bundle values are nanoseconds ({@code LatencyHistogram.Snapshot}); operations are
 * milliseconds. {@code POST /lab/latency/reset} starts a new window. On images whose framework
 * predates the bundle latencies, {@code bundle} says {@code unavailable} (reflection, like
 * {@link com.evento.lab.cluster.support.FrameworkFeatures}).
 */
@RestController
public class LabLatencyController {

    private final BundleRunner runner;
    private final LatencySamples samples;

    public LabLatencyController(BundleRunner runner, LatencySamples samples) {
        this.runner = runner;
        this.samples = samples;
    }

    @GetMapping("/lab/latency")
    public Map<String, Object> latency() {
        var out = new LinkedHashMap<String, Object>();
        out.put("instance", runner.deployment().instanceId());
        var latencies = bundleLatencies();
        if (latencies == null) {
            out.put("bundle", "unavailable");
        } else {
            out.put("requestRtt", call(call(latencies, "requestRtt"), "snapshot"));
            out.put("handlers", call(call(latencies, "handlers"), "snapshot"));
        }
        out.put("operations", samples.summary());
        return out;
    }

    @PostMapping("/lab/latency/reset")
    public Map<String, Object> reset() {
        var latencies = bundleLatencies();
        if (latencies != null) call(latencies, "reset");
        samples.reset();
        return Map.of("reset", true);
    }

    private Object bundleLatencies() {
        return runner.bundle().map(b -> call(b, "getLatencies")).orElse(null);
    }

    private static Object call(Object target, String method) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
