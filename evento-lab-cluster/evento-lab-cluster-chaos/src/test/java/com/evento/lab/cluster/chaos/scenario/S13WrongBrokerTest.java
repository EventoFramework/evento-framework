package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Cluster;
import com.evento.lab.cluster.chaos.RequestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S13: {@code gateway-europa} behind the EU bus VIP ({@code bus-eu:3030})
 * with the 2.4.6 wrong-broker guard {@code expectedServerInstanceId=evento-eu}.
 * When evento-eu dies the VIP hands the bundle a healthy server of the same
 * cluster, which the single-id guard refuses ({@code event=server_instance_mismatch}
 * loop, G13). With an expected cluster name it would accept any
 * {@code evento-lab-cluster} server and still refuse a foreign one (that half
 * needs a fourth server with another cluster name and is not run here).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S13WrongBrokerTest extends AbstractScenario {

    private static final String SERVICE = com.evento.lab.cluster.chaos.LabConfig.regionService("EUROPA");

    @Override
    protected String id() {
        return "S13";
    }

    @Override
    protected String title() {
        return "Wrong-broker guard and server identity";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G13");
    }

    @Test
    void wrongBroker() throws Exception {
        scenario(() -> {
            onCleanup(() -> {
                cluster.ensureRunning("evento-eu");
                cluster.waitReady("evento-eu", Duration.ofSeconds(420));
                cluster.recreateDefault(SERVICE);
                cluster.waitReady(SERVICE, Duration.ofSeconds(300));
            });
            var vip = System.getenv().getOrDefault("S13_BUS_ADDRESSES", "bus-eu:3030");
            cluster.recreateWithEnv(SERVICE, Map.of(
                    "EVENTO_SERVER_ADDRESSES", vip,
                    "EVENTO_EXPECTED_SERVER_ID", "evento-eu"));
            var ready = cluster.waitReady(SERVICE, Duration.ofSeconds(300));
            report.event(SERVICE + " via " + vip + " expecting evento-eu, ready=" + ready);
            var before = probe(3);
            report.observe("guard accepts the expected broker", "acknowledged through the VIP",
                    ack(before) + "/3 acknowledged " + kinds(before), ack(before) == 3);

            var since = Instant.now();
            cluster.kill("evento-eu");
            report.event("killed evento-eu");
            var window = envSeconds("S13_WAIT_S", 60);
            Cluster.sleep(window);
            var after = probe(3);
            var logs = cluster.logsSince(SERVICE, since);
            var mismatches = Cluster.countMarkers(logs, List.of("event=server_instance_mismatch"))
                    .get("event=server_instance_mismatch");
            report.attach(SERVICE + " mismatch lines", Cluster.grep(logs, "server_instance_mismatch", 10));
            report.metric("serverInstanceMismatches", mismatches);
            report.observe("failover to a healthy server of the same cluster",
                    "the bundle accepts any evento-lab-cluster server behind the VIP and EUROPA works again",
                    "the single expected id rejects every healthy failover target: server_instance_mismatch loop (G13)",
                    ack(after) + "/3 acknowledged " + kinds(after) + "; " + mismatches + " mismatch log lines in "
                            + window.toSeconds() + " s",
                    ack(after) == 3, mismatches > 0, null);
            report.note("Foreign-cluster rejection (the other half of the G13 fix) needs a 4th server with "
                    + "EVENTO_CLUSTER_NAME set to something else; not part of the default topology.");
            markers(List.of(SERVICE, "lb"), since, MARKERS);
        });
    }

    private List<RequestRecord> probe(int n) {
        var out = new java.util.ArrayList<RequestRecord>();
        for (int i = 0; i < n; i++) {
            out.add(gateway.create("EUROPA", gateway.newOrderId(prefix, "EUROPA"), UUID.randomUUID().toString()));
        }
        return out;
    }

    private static long ack(List<RequestRecord> rs) {
        return rs.stream().filter(r -> r.outcome() == RequestRecord.Outcome.ACK).count();
    }
}
