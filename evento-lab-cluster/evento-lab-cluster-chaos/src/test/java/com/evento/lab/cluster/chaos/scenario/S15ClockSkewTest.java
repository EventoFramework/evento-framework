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
 * S15 (optional): a region JVM whose clock runs ahead. The server's fetch
 * staleness gate compares the client's request timestamp with its own clock
 * ({@code EventStoreRequestHandlerBridge.java:57-63}), so a skewed consumer's
 * fetches can be dropped. Docker cannot skew one container's clock without
 * libfaketime, which the lab image does not ship; set
 * {@code S15_FAKETIME_ENV} to the environment that enables it in your image
 * (e.g. {@code LD_PRELOAD=/usr/lib/faketime/libfaketime.so.1,FAKETIME=+40s})
 * to run the fault. Without it the scenario records INCONCLUSIVE.
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S15ClockSkewTest extends AbstractScenario {

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
        return List.of("evento-eu", "region-europa", "region-africa", "lb");
    }

    @Test
    void clockSkew() throws Exception {
        scenario(() -> {
            var faketime = System.getenv("S15_FAKETIME_ENV");
            if (faketime == null || faketime.isBlank()) {
                report.observe("skewed region consumer",
                        "fetches of a +40 s skewed JVM are served (or rejected with a clear error)",
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
                cluster.recreateDefault("region-africa");
                cluster.waitReady("region-africa", Duration.ofSeconds(300));
            });
            cluster.recreateWithEnv("region-africa", Map.copyOf(env));
            cluster.waitReady("region-africa", Duration.ofSeconds(300));
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
                    "the AFRICA projector (clock +skew) still projects its orders within 60 s",
                    "the staleness gate drops the skewed consumer's fetches: the orders never appear",
                    seen ? "all " + ids.size() + " orders projected" : "orders not projected within 60 s",
                    seen, !seen, null);
            markers(List.of("evento-eu", "region-africa"), since, List.of("stale", "fetch", "event=supervisor_session_failed"));
        });
    }
}
