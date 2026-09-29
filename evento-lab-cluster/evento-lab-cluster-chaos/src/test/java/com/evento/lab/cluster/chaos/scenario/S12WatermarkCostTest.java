package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.Json;
import com.evento.lab.cluster.chaos.LabConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * S12: what the EUROPA-only projector pays for traffic it does not consume.
 * The other six regions write at a high rate; {@code region-europa} is run
 * once with the default checkpoint mode and once with
 * {@code CONSUMER_CHECKPOINT_MODE=WATERMARK}. Before G10 the watermark's
 * {@code recordAbsent} walks every sequence number between the cursor and
 * the top of each batch (6/7 of global volume for a one-region consumer), so
 * its CPU cost per global event grows with the other regions' traffic.
 *
 * <p>Measured per phase from the JVM itself ({@code /lab/stats} through the
 * LB): process CPU time and GC per 1000 events appended cluster-wide, and the
 * heap trend.
 *
 * <p>Knobs: {@code S12_RATE} (orders/min/region, 120), {@code S12_PHASE_S} (60).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S12WatermarkCostTest extends AbstractScenario {

    private static final List<String> OTHERS = List.of("AFRICA", "AMERICA", "SUD_AMERICA", "CINA", "INDIA",
            "AUSTRALIA_NZ");

    @Override
    protected String id() {
        return "S12";
    }

    @Override
    protected String title() {
        return "Per-context WATERMARK consumer cost";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G10", "G20");
    }

    @Test
    void watermarkCost() throws Exception {
        scenario(() -> {
            onCleanup(() -> {
                cluster.recreateDefault("region-europa");
                cluster.waitReady("region-europa", Duration.ofSeconds(300));
            });
            var phases = new LinkedHashMap<String, Map<String, Object>>();
            for (var mode : List.of("DEFAULT", "WATERMARK")) {
                cluster.recreateWithEnv("region-europa", Map.of("CONSUMER_CHECKPOINT_MODE", mode));
                var ready = cluster.waitReady("region-europa", Duration.ofSeconds(300));
                var who = gateway.getBody("EUROPA", "/whoami");
                report.event("region-europa in " + mode + " ready=" + ready + " whoami=" + who);
                if (who != null && !who.contains("\"checkpointMode\":\"" + mode + "\"")
                        && !who.contains("\"checkpointMode\" : \"" + mode + "\"")) {
                    report.note("region-europa did not report checkpointMode=" + mode + ": " + who);
                }
                Cluster.sleep(Duration.ofSeconds(10));
                phases.put(mode, measure(mode));
            }
            report.metric("phases", phases);
            var def = (Double) phases.get("DEFAULT").get("cpuMsPer1kEvents");
            var wm = (Double) phases.get("WATERMARK").get("cpuMsPer1kEvents");
            Double ratio = def == null || wm == null || def <= 0 ? null : wm / def;
            report.metric("watermarkToDefaultCpuRatio", ratio);
            report.observe("EUROPA consumer cost vs global volume",
                    "WATERMARK costs about the same as DEFAULT (ratio <= 1.5): flat in the other regions' traffic",
                    "recordAbsent is O(numeric gap): WATERMARK burns CPU/heap proportional to global volume (G10)",
                    ratio == null ? "not measurable: " + phases : String.format("CPU per 1k global events: DEFAULT %.1f ms,"
                            + " WATERMARK %.1f ms (x%.2f)", def, wm, ratio),
                    ratio == null ? null : ratio <= 1.5, ratio != null && ratio > 1.5,
                    "APES (G20) would make the gap ~4M ids/ms; the lab runs CPES only.");
        });
    }

    private Map<String, Object> measure(String mode) {
        var phase = envSeconds("S12_PHASE_S", 60);
        var samples = new ArrayList<Map<String, Object>>();
        var stop = new AtomicBoolean(false);
        var sampler = Thread.ofVirtual().start(() -> {
            while (!stop.get()) {
                var body = gateway.getBody("EUROPA", "/lab/stats");
                var row = new LinkedHashMap<String, Object>();
                row.put("t", System.currentTimeMillis());
                if (body != null) {
                    var n = Json.read(body);
                    row.put("cpuNanos", n.path("processCpuTimeNanos").asLong(-1));
                    row.put("heap", n.path("heapUsedBytes").asLong(-1));
                    row.put("gcMillis", n.path("gcMillis").asLong(-1));
                }
                row.put("dockerStats", cluster.stats("region-europa"));
                samples.add(row);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        long head0;
        try {
            head0 = db.esHead();
        } catch (Exception e) {
            head0 = -1;
        }
        var recs = load(OTHERS, env("S12_RATE", 120), 1, 0.0, phase, null);
        long head1;
        try {
            head1 = db.esHead();
        } catch (Exception e) {
            head1 = -1;
        }
        Cluster.sleep(Duration.ofSeconds(10)); // let the consumer drain
        stop.set(true);
        sampler.interrupt();
        var out = new LinkedHashMap<String, Object>();
        out.put("mode", mode);
        out.put("requests", recs.size());
        out.put("globalEventsAppended", head1 - head0);
        var withCpu = samples.stream().filter(s -> s.get("cpuNanos") instanceof Long l && l > 0).toList();
        if (withCpu.size() >= 2 && head1 > head0) {
            var first = withCpu.getFirst();
            var last = withCpu.getLast();
            double cpuMs = ((Long) last.get("cpuNanos") - (Long) first.get("cpuNanos")) / 1e6;
            double gcMs = (Long) last.get("gcMillis") - (Long) first.get("gcMillis");
            out.put("cpuMs", cpuMs);
            out.put("cpuMsPer1kEvents", cpuMs / ((head1 - head0) / 1000.0));
            out.put("gcMsPer1kEvents", gcMs / ((head1 - head0) / 1000.0));
            out.put("heapFirst", first.get("heap"));
            out.put("heapLast", last.get("heap"));
            out.put("heapMax", withCpu.stream().mapToLong(s -> (Long) s.get("heap")).max().orElse(-1));
        } else {
            out.put("cpuMsPer1kEvents", null);
            report.note(mode + ": /lab/stats not reachable through the LB, only docker stats recorded");
        }
        report.attach("samples " + mode, samples.stream().map(Object::toString).toList());
        return out;
    }
}
