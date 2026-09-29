package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.RequestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S15 (optional): a consumer JVM whose clock runs behind ({@code order-query-eu},
 * which projects EUROPA and AFRICA). The server's fetch
 * staleness gate compares the client's request timestamp with its own clock
 * ({@code EventStoreRequestHandlerBridge.java:57-63}), so a skewed consumer's
 * fetches can be dropped. Docker cannot skew one container's clock without
 * libfaketime, which the lab image does not ship; set
 * {@code S15_FAKETIME_ENV} to the environment that enables it in your image
 * (e.g. {@code LD_PRELOAD=/usr/lib/x86_64-linux-gnu/faketime/libfaketime.so.1,FAKETIME=-40s,FAKETIME_DONT_FAKE_MONOTONIC=1})
 * to run the fault, and {@code S15_IMAGE} to an image that has the library
 * ({@code docker/faketime/Dockerfile} derives one from the lab image). Without
 * {@code S15_FAKETIME_ENV} the scenario records INCONCLUSIVE.
 *
 * <p>The clock must run BEHIND by more than the gate (30 s): a clock ahead makes every
 * request look younger than it is and never trips the gate (L18: the first S15 used +40 s).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S15ClockSkewTest extends AbstractScenario {

    private static final String SKEWED = com.evento.lab.cluster.chaos.LabConfig.queryService("AFRICA");

    @Override
    protected String id() {
        return "S15";
    }

    @Override
    protected String title() {
        return "Clock skew on one region JVM (optional)";
    }

    @Override
    protected List<String> gaps() {
        return List.of("fetch staleness gate (Low)", "G20");
    }

    @Override
    protected List<String> requiredServices() {
        return List.of("evento-eu", SKEWED, "gateway-europa", "gateway-africa", "lb");
    }

    @Test
    void clockSkew() throws Exception {
        scenario(() -> {
            var faketime = System.getenv("S15_FAKETIME_ENV");
            if (faketime == null || faketime.isBlank()) {
                report.observe("skewed region consumer",
                        "fetches of a JVM whose clock is 40 s behind are served (or rejected with a clear error)",
                        "the staleness gate silently drops the skewed region's fetches",
                        "not run: S15_FAKETIME_ENV not set (no libfaketime in the image)", null, null,
                        "Documented only; see the class javadoc to enable it.");
                return;
            }
            var env = new java.util.LinkedHashMap<String, String>();
            for (var kv : faketime.split(",")) {
                var i = kv.indexOf('=');
                if (i > 0) env.put(kv.substring(0, i).trim(), kv.substring(i + 1).trim());
            }
            onCleanup(() -> {
                cluster.recreateDefault(SKEWED);
                cluster.waitReady(SKEWED, Duration.ofSeconds(300));
            });
            var image = System.getenv("S15_IMAGE");
            report.param("skewEnv", env);
            report.param("skewImage", image == null ? "(compose default)" : image);
            cluster.recreateWithEnv(SKEWED, Map.copyOf(env), image);
            cluster.waitReady(SKEWED, Duration.ofSeconds(300));
            // is the fault real? 'date' in the container inherits LD_PRELOAD/FAKETIME
            var clock = cluster.docker(Duration.ofSeconds(15), "exec", SKEWED, "date", "+%s");
            report.param("skewedClockMinusHostSeconds", clock.ok()
                    ? String.valueOf(Long.parseLong(clock.out().trim()) - Instant.now().getEpochSecond())
                    : "n/a: " + clock.combined());
            var since = Instant.now();
            var ids = new java.util.ArrayList<String>();
            for (int i = 0; i < 5; i++) {
                var id = gateway.newOrderId(prefix, "AFRICA");
                // written through EUROPA's gateway with AFRICA's context: only the consumer is skewed
                if (gateway.create("EUROPA", id, UUID.randomUUID().toString(), "AFRICA").outcome()
                        == RequestRecord.Outcome.ACK) ids.add(id);
            }
            var seen = com.evento.lab.cluster.chaos.Cluster.waitUntil(Duration.ofSeconds(60), Duration.ofSeconds(3),
                    () -> ids.stream().allMatch(id -> gateway.getRegional("AFRICA", id).outcome()
                            == RequestRecord.Outcome.ACK));
            report.observe("skewed region consumer",
                    "the AFRICA projector (skewed clock) still projects its orders within 60 s",
                    "the staleness gate drops the skewed consumer's fetches: the orders never appear",
                    seen ? "all " + ids.size() + " orders projected" : "orders not projected within 60 s",
                    seen, !seen, null);
            markers(List.of("evento-eu", SKEWED), since, List.of("stale", "fetch", "event=supervisor_session_failed"));
        });
    }
}
