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
 * S13: server identity and the wrong-broker guard, with {@code gateway-europa} behind the EU bus
 * VIP ({@code bus-eu:3030}), in three variants:
 * <ul>
 *   <li>C, foreign cluster: the gateway expects {@code EVENTO_CLUSTER_NAME=foreign-cluster} while
 *       every server announces {@code evento-lab-cluster}. The G13 fix refuses the session
 *       ({@code event=cluster_name_mismatch}); 2.4.6 has no cluster identity and attaches.</li>
 *   <li>A, legacy single-id guard {@code EVENTO_EXPECTED_SERVER_ID=evento-eu} (2.4.6's only
 *       option): when evento-eu dies the VIP hands the bundle a healthy server of the same cluster,
 *       which the single id refuses ({@code event=server_instance_mismatch} loop). This is the
 *       guard's contract on both image sets; it is why G13 needed a cluster-level identity.</li>
 *   <li>B, cluster-name guard only (the compose default {@code EVENTO_CLUSTER_NAME}, no expected
 *       id): after the kill the bundle must accept any {@code evento-lab-cluster} server behind the
 *       VIP and EUROPA must work again, with the guard still active.</li>
 * </ul>
 * Before L21 only variant A ran, so the G13 fix could never show on {@code :after}. Between A and
 * B the other EU JVMs, which failed over to another server when evento-eu was killed, must fail
 * back to it on their own once it is healthy again (N16).
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
        return List.of("G13", "N16");
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
            var window = envSeconds("S13_WAIT_S", 60);

            // ---- C: a bundle that expects another cluster -----------------------------------------
            var sinceC = Instant.now();
            cluster.recreateWithEnv(SERVICE, Map.of(
                    "EVENTO_SERVER_ADDRESSES", vip,
                    "EVENTO_EXPECTED_SERVER_ID", "",
                    "EVENTO_CLUSTER_NAME", "foreign-cluster"));
            var readyC = cluster.waitReady(SERVICE, window);
            var whoC = cluster.httpGetInside(SERVICE, "/whoami");
            var featureC = feature(whoC, "expectedClusterName");
            var logsC = cluster.logsSince(SERVICE, sinceC);
            var clusterMismatches = Cluster.countMarkers(logsC, List.of("event=cluster_name_mismatch"))
                    .get("event=cluster_name_mismatch");
            var attached = whoC != null && whoC.replace(" ", "").contains("\"busState\":\"READY\"");
            report.metric("C.clusterNameMismatches", clusterMismatches);
            report.metric("C.expectedClusterNameFeature", featureC);
            report.attach(SERVICE + " C cluster mismatch lines", Cluster.grep(logsC, "cluster_name_mismatch", 5));
            report.observe("C: bundle expecting a foreign cluster",
                    "the session is refused (cluster_name_mismatch) and the bundle never becomes READY",
                    "no cluster identity in 2.4.6: the bundle attaches to whatever server answers (G13)",
                    "healthy=" + readyC + ", busState READY=" + attached + ", " + clusterMismatches
                            + " cluster_name_mismatch line(s), feature " + featureC,
                    !attached && clusterMismatches > 0, attached, null);

            // ---- A: legacy single-id guard ---------------------------------------------------------
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
            report.event("A: killed evento-eu");
            Cluster.sleep(window);
            var after = probe(3);
            var logs = cluster.logsSince(SERVICE, since);
            var mismatches = Cluster.countMarkers(logs, List.of("event=server_instance_mismatch"))
                    .get("event=server_instance_mismatch");
            report.attach(SERVICE + " mismatch lines", Cluster.grep(logs, "server_instance_mismatch", 10));
            report.metric("serverInstanceMismatches", mismatches);
            report.observe("A: legacy single-id guard on failover",
                    "by contract the single expected id refuses every other server, even a healthy one of the"
                            + " same cluster (recorded on both image sets; the fix is variant B's cluster name)",
                    "the single expected id rejects every healthy failover target: server_instance_mismatch loop (G13)",
                    ack(after) + "/3 acknowledged " + kinds(after) + "; " + mismatches + " mismatch log lines in "
                            + window.toSeconds() + " s",
                    ack(after) == 3, mismatches > 0, null);
            markers(List.of(SERVICE, "lb"), since, MARKERS);

            // ---- B: cluster-name guard only --------------------------------------------------------
            // The other EU JVMs (direct address lists) failed over to evento-am during A. SERVICE is
            // behind the VIP with one address: it has nothing to fail back to and is recreated below.
            var euJvms = com.evento.lab.cluster.chaos.LabConfig.cellJvms("EU").stream()
                    .filter(j -> !j.equals(SERVICE)).toList();
            var remoteDuringA = remoteJvms("EU", euJvms);
            report.metric("A.bundlesRemoteWhileEuDown", remoteDuringA);
            cluster.ensureRunning("evento-eu");
            var euBack = cluster.waitReady("evento-eu", Duration.ofSeconds(420));
            var euBackAt = Instant.now();
            report.event("evento-eu back, ready=" + euBack);
            // N16: they must come home by themselves once evento-eu has been healthy for the
            // stability window. B needs them home, otherwise the gateway lands on evento-eu with
            // no handler there (G1).
            var failbackWait = envSeconds("S13_FAILBACK_WAIT_S", 180);
            var stillRemote = awaitFailback("EU", euJvms, failbackWait);
            report.metric("A.bundlesStillRemoteAfterEuReturned", stillRemote);
            report.metric("A.allHomeSecondsAfterEuReady", stillRemote.isEmpty()
                    ? Duration.between(euBackAt, Instant.now()).toMillis() / 1000.0 : null);
            report.observe("A: EU bundles fail back to evento-eu once it is healthy again (N16)",
                    "every EU JVM that failed over to another server is attached to evento-eu again,"
                            + " without a restart, within " + failbackWait.toSeconds() + " s",
                    "no failback: they stay on the remote server until restarted (N16)",
                    remoteDuringA.isEmpty() ? "no EU JVM had failed over"
                            : remoteDuringA.size() + " JVM(s) remote while evento-eu was down " + remoteDuringA
                                    + "; still remote after the wait: " + stillRemote,
                    remoteDuringA.isEmpty() ? null : stillRemote.isEmpty(), !stillRemote.isEmpty(), null);
            // Safety net for the images without the fix: B must start from the nominal placement.
            var leftRemote = restorePlacement();
            report.metric("A.bundlesRestartedIntoPlace", leftRemote);
            cluster.recreateWithEnv(SERVICE, Map.of(
                    "EVENTO_SERVER_ADDRESSES", vip,
                    "EVENTO_EXPECTED_SERVER_ID", ""));
            var readyB = cluster.waitReady(SERVICE, Duration.ofSeconds(300));
            var whoB = cluster.httpGetInside(SERVICE, "/whoami");
            var featureB = feature(whoB, "expectedClusterName");
            report.metric("B.expectedClusterNameFeature", featureB);
            var beforeB = probe(3);
            var sinceB = Instant.now();
            cluster.kill("evento-eu");
            report.event("B: killed evento-eu");
            Cluster.sleep(window);
            var afterB = probe(3);
            var logsB = cluster.logsSince(SERVICE, sinceB);
            report.attach(SERVICE + " B handshakes after the kill", Cluster.grep(logsB, "event=handshake_complete", 5));
            var guardOn = featureB != null && featureB.startsWith("available");
            report.metric("B.probesBeforeKill", ack(beforeB) + "/3");
            report.metric("B.probesAfterKill", ack(afterB) + "/3 " + kinds(afterB));
            report.observe("B: failover to a healthy server of the same cluster (cluster-name guard)",
                    "the bundle accepts any evento-lab-cluster server behind the VIP, EUROPA works again,"
                            + " and the cluster-name guard is active",
                    "no cluster-name guard in 2.4.6: failover works only because nothing is checked (G13)",
                    "ready=" + readyB + ", " + ack(beforeB) + "/3 before the kill " + kinds(beforeB) + ", "
                            + ack(afterB) + "/3 after "
                            + kinds(afterB) + "; guard " + featureB,
                    ack(afterB) == 3 && guardOn, !guardOn, null);
            markers(List.of(SERVICE), sinceB, MARKERS);
        });
    }

    /** "available: value" / "unavailable" for one entry of /whoami frameworkFeatures, or null. */
    static String feature(String whoami, String name) {
        if (whoami == null) return null;
        var n = com.evento.lab.cluster.chaos.Json.read(whoami).path("frameworkFeatures");
        for (var f : n) {
            if (name.equals(f.path("feature").asText())) {
                return f.path("available").asBoolean() ? "available: " + f.path("value").asText() : "unavailable";
            }
        }
        return "unavailable (not listed)";
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
