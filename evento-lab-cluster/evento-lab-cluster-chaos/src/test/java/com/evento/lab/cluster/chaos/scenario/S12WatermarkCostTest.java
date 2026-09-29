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
 * The other six regions write at a high rate; {@code order-query-eu} is
 * redeployed with {@code EVENTO_CONTEXTS=EUROPA} (configuration only: AFRICA
 * becomes foreign traffic for it) once with the default checkpoint mode and
 * once with {@code EVENTO_CHECKPOINT_MODE=WATERMARK}. Before G10 the watermark's
 * {@code recordAbsent} walks every sequence number between the cursor and
 * the top of each batch (6/7 of global volume for a one-region consumer), so
 * its CPU cost per global event grows with the other regions' traffic.
 *
 * <p>Measured per phase from the JVM itself ({@code /lab/stats}, read inside
 * the container): process CPU time and GC per 1000 events appended
 * cluster-wide, and the heap trend.
 *
 * <p>The JVM's idle cost (fetch polling, heartbeats, JIT) is time-based, not event-based, and in
 * a 60 s phase it is of the same order as the load-attributable cost. Each phase therefore first
 * samples the idle CPU rate for {@code S12_IDLE_S} and subtracts it (L19). When the two phases'
 * load-attributable CPU differ by less than {@code S12_NOISE_FLOOR_MS}, the ratio is noise and the
 * verdict is INCONCLUSIVE.
 *
 * <p>Knobs: {@code S12_RATE} (orders/min/region, 120), {@code S12_PHASE_S} (60),
 * {@code S12_IDLE_S} (30), {@code S12_NOISE_FLOOR_MS} (1000).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S12WatermarkCostTest extends AbstractScenario {

    private static final String SERVICE = LabConfig.queryService("EUROPA");

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
                cluster.recreateDefault(SERVICE);
                cluster.waitReady(SERVICE, Duration.ofSeconds(300));
            });
            var phases = new LinkedHashMap<String, Map<String, Object>>();
            for (var mode : List.of("DEFAULT", "WATERMARK")) {
                cluster.recreateWithEnv(SERVICE, Map.of("EVENTO_CHECKPOINT_MODE", mode, "EVENTO_CONTEXTS", "EUROPA"));
                var ready = cluster.waitReady(SERVICE, Duration.ofSeconds(300));
                var who = cluster.httpGetInside(SERVICE, "/whoami");
                report.event(SERVICE + " (EUROPA only) in " + mode + " ready=" + ready + " whoami=" + who);
                if (who != null && !who.contains("\"checkpointMode\":\"" + mode + "\"")
                        && !who.contains("\"checkpointMode\" : \"" + mode + "\"")) {
                    report.note(SERVICE + " did not report checkpointMode=" + mode + ": " + who);
                }
                Cluster.sleep(Duration.ofSeconds(10));
                phases.put(mode, measure(mode));
            }
            report.metric("phases", phases);
            var def = (Double) phases.get("DEFAULT").get("loadCpuMsPer1kEvents");
            var wm = (Double) phases.get("WATERMARK").get("loadCpuMsPer1kEvents");
            var defLoad = (Double) phases.get("DEFAULT").get("loadCpuMs");
            var wmLoad = (Double) phases.get("WATERMARK").get("loadCpuMs");
            Double ratio = def == null || wm == null || def <= 0 ? null : wm / def;
            report.metric("watermarkToDefaultCpuRatio", ratio);
            double floor = LabConfig.envInt("S12_NOISE_FLOOR_MS", 1000);
            boolean belowNoise = defLoad != null && wmLoad != null && Math.abs(wmLoad - defLoad) < floor;
            report.metric("noiseFloorMs", floor);
            Boolean correct = ratio == null || belowNoise ? null : ratio <= 1.5;
            report.observe("EUROPA consumer cost vs global volume",
                    "WATERMARK costs about the same as DEFAULT (ratio <= 1.5): flat in the other regions' traffic",
                    "recordAbsent is O(numeric gap): WATERMARK burns CPU/heap proportional to global volume (G10)",
                    ratio == null ? notMeasurable(phases) : String.format("load-attributable CPU per 1k global"
                            + " events (idle rate subtracted): DEFAULT %.1f ms, WATERMARK %.1f ms (x%.2f);"
                            + " load CPU DEFAULT %.0f ms, WATERMARK %.0f ms%s", def, wm, ratio, defLoad, wmLoad,
                            belowNoise ? ", difference below the " + (long) floor + " ms noise floor" : ""),
                    correct, ratio != null && !belowNoise && ratio > 1.5,
                    "APES (G20) would make the gap ~4M ids/ms; the lab runs CPES only, where the gap per batch is"
                            + " the other regions' events, so G10 can only show as a small per-event cost.");
        });
    }

    /** Why no ratio: usually the CPU under load did not rise above the idle rate in either phase. */
    static String notMeasurable(Map<String, Map<String, Object>> phases) {
        var sb = new StringBuilder("no load-attributable CPU to compare (CPU under load <= idle rate);");
        for (var e : phases.entrySet()) {
            var p = e.getValue();
            sb.append(String.format(" %s: %s ms CPU over %s s for %s foreign events, idle %.1f ms/s;", e.getKey(),
                    p.get("cpuMs"), p.get("sampledSeconds"), p.get("globalEventsAppended"),
                    p.get("idleCpuMsPerSecond") instanceof Double d ? d : Double.NaN));
        }
        return sb.toString();
    }

    /** CPU/heap samples of {@link #SERVICE} every 5 s while {@code body} runs (rows without CPU dropped). */
    private List<Map<String, Object>> sampled(Runnable body) {
        var samples = java.util.Collections.synchronizedList(new ArrayList<Map<String, Object>>());
        var stop = new AtomicBoolean(false);
        var sampler = Thread.ofVirtual().start(() -> {
            while (!stop.get()) {
                var stat = cluster.httpGetInside(SERVICE, "/lab/stats");
                var row = new LinkedHashMap<String, Object>();
                row.put("t", System.currentTimeMillis());
                if (stat != null) {
                    var n = Json.read(stat);
                    row.put("cpuNanos", n.path("processCpuTimeNanos").asLong(-1));
                    row.put("heap", n.path("heapUsedBytes").asLong(-1));
                    row.put("gcMillis", n.path("gcMillis").asLong(-1));
                }
                row.put("dockerStats", cluster.stats(SERVICE));
                samples.add(row);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        try {
            body.run();
        } finally {
            stop.set(true);
            sampler.interrupt();
            try {
                sampler.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (samples) {
            return samples.stream().filter(r -> r.get("cpuNanos") instanceof Long l && l > 0).toList();
        }
    }

    private Map<String, Object> measure(String mode) {
        var phase = envSeconds("S12_PHASE_S", 60);
        var out = new LinkedHashMap<String, Object>();
        out.put("mode", mode);

        // idle rate first: fetch polling, heartbeats and JIT cost time, not events (L19)
        var idle = sampled(() -> Cluster.sleep(envSeconds("S12_IDLE_S", 30)));
        Double idleMsPerS = null;
        if (idle.size() >= 2) {
            double ms = ((Long) idle.getLast().get("cpuNanos") - (Long) idle.getFirst().get("cpuNanos")) / 1e6;
            double s = ((Long) idle.getLast().get("t") - (Long) idle.getFirst().get("t")) / 1000.0;
            idleMsPerS = s > 0 ? ms / s : null;
        }
        out.put("idleCpuMsPerSecond", idleMsPerS);

        var head = new long[]{-1, -1};
        var requests = new int[]{0};
        var samples = sampled(() -> {
            try {
                head[0] = db.esHead();
            } catch (Exception e) {
                head[0] = -1;
            }
            requests[0] = load(OTHERS, env("S12_RATE", 120), 1, 0.0, phase, null).size();
            try {
                head[1] = db.esHead();
            } catch (Exception e) {
                head[1] = -1;
            }
            Cluster.sleep(Duration.ofSeconds(10)); // let the consumer drain
        });
        long events = head[0] < 0 || head[1] < 0 ? -1 : head[1] - head[0];
        out.put("requests", requests[0]);
        out.put("globalEventsAppended", events);
        if (samples.size() >= 2 && events > 0) {
            var first = samples.getFirst();
            var last = samples.getLast();
            double cpuMs = ((Long) last.get("cpuNanos") - (Long) first.get("cpuNanos")) / 1e6;
            double seconds = ((Long) last.get("t") - (Long) first.get("t")) / 1000.0;
            double gcMs = (Long) last.get("gcMillis") - (Long) first.get("gcMillis");
            double loadCpuMs = idleMsPerS == null ? cpuMs : Math.max(0, cpuMs - idleMsPerS * seconds);
            out.put("cpuMs", cpuMs);
            out.put("sampledSeconds", seconds);
            out.put("cpuMsPer1kEvents", cpuMs / (events / 1000.0));
            out.put("loadCpuMs", loadCpuMs);
            out.put("loadCpuMsPer1kEvents", loadCpuMs / (events / 1000.0));
            out.put("gcMsPer1kEvents", gcMs / (events / 1000.0));
            out.put("heapFirst", first.get("heap"));
            out.put("heapLast", last.get("heap"));
            out.put("heapMax", samples.stream().mapToLong(r -> (Long) r.get("heap")).max().orElse(-1));
        } else {
            out.put("loadCpuMsPer1kEvents", null);
            out.put("loadCpuMs", null);
            report.note(mode + ": /lab/stats not readable in " + SERVICE + ", only docker stats recorded");
        }
        report.attach("samples " + mode, samples.stream().map(Object::toString).toList());
        return out;
    }
}
