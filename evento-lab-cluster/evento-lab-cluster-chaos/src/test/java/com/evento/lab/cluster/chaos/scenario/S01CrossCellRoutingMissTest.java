package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.RequestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S1: the AM cell keeps its gateways but loses its handlers
 * ({@code cell-core-am} stopped). Handlers for the same commands are live in
 * EU and APAC, yet evento-am has no route to them (G1: servers are islands).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S01CrossCellRoutingMissTest extends AbstractScenario {

    @Override
    protected String id() {
        return "S01";
    }

    @Override
    protected String title() {
        return "Cross-cell routing miss";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G1");
    }

    @Test
    void crossCellRoutingMiss() throws Exception {
        scenario(() -> {
            var probes = LabConfig.envInt("S01_PROBES", 5);
            var since = Instant.now();
            onCleanup(() -> {
                cluster.ensureRunning("cell-core-am");
                cluster.waitReady("cell-core-am", Duration.ofSeconds(300));
                var back = waitCommandsWork("AMERICA", Duration.ofSeconds(120));
                report.metric("americaRecoveredAfterRestartSeconds", back == null ? null : back.toMillis() / 1000.0);
            });

            cluster.stop("cell-core-am");
            report.event("stopped cell-core-am");
            // give evento-am time to notice the bundle is gone
            Cluster.sleep(Duration.ofSeconds(LabConfig.envInt("S01_SETTLE_S", 10)));

            var america = new ArrayList<RequestRecord>();
            var europa = new ArrayList<RequestRecord>();
            for (int i = 0; i < probes; i++) {
                america.add(gateway.create("AMERICA", gateway.newOrderId(prefix, "AMERICA"), UUID.randomUUID().toString()));
                europa.add(gateway.create("EUROPA", gateway.newOrderId(prefix, "EUROPA"), UUID.randomUUID().toString()));
            }
            report.event("sent " + probes + " CreateOrder from AMERICA and EUROPA");
            var americaAck = america.stream().filter(r -> r.outcome() == RequestRecord.Outcome.ACK).count();
            var noHandler = america.stream().filter(r -> "no_handler".equals(r.kind())).count();
            report.metric("americaKinds", kinds(america));
            report.metric("americaErrors", america.stream().map(RequestRecord::error).distinct().toList());

            report.observe("AMERICA command with no handler in its cell",
                    "routed to a live handler in EU or APAC and acknowledged (needs the G1 server mesh)",
                    "fails immediately with 'no handler for CreateOrderCommand'",
                    americaAck + "/" + probes + " acknowledged; " + noHandler + " no_handler; kinds " + kinds(america),
                    americaAck == probes, noHandler > 0,
                    "The cell architecture exists because of this: every cell must host a full handler set.");
            report.observe("EUROPA control", "unaffected: all acknowledged",
                    europa.stream().filter(r -> r.outcome() == RequestRecord.Outcome.ACK).count() + "/" + probes,
                    europa.stream().allMatch(r -> r.outcome() == RequestRecord.Outcome.ACK));
            var latency = america.stream().mapToLong(RequestRecord::latencyMs).max().orElse(0);
            report.metric("americaMaxLatencyMs", latency);
            report.observe("failure is immediate", "a routing miss fails fast (< 2 s), not after a timeout",
                    "max latency " + latency + " ms", latency < 2000);

            markers(List.of("evento-am", "region-america"), since, MARKERS);
        });
    }
}
