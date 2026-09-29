package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.Json;
import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.LatencyStats;
import com.evento.lab.cluster.chaos.RequestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * S3: kill {@code evento-apac} under load and keep it dead for a while.
 *
 * <p>Before the fixes: APAC bundles with direct address lists keep redialling
 * the dead server (G2, only the first address is used) and are stranded; with
 * {@code BUS_VIA_LB=true} they reach another server through the stream VIP and
 * re-register, racing the catalog (G8). In-flight calls hang for the 30 s
 * request timeout (G4). After the fixes: address rotation, an immediate
 * {@code BrokerChangedException}, a clean catalog, and once {@code evento-apac}
 * is back and healthy for the stability window, every APAC JVM fails back to it
 * without a restart (N16).
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S03KillServerTest extends AbstractScenario {

    static final List<String> APAC = LabConfig.regionsOfCell("APAC");
    static final List<String> APAC_JVMS = LabConfig.cellJvms("APAC");

    @Override
    protected String id() {
        return "S03";
    }

    @Override
    protected String title() {
        return "Kill an evento-server";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G2", "G4", "G5", "G8", "G13", "N16");
    }

    @Test
    void killServer() throws Exception {
        scenario(() -> {
            onCleanup(() -> {
                cluster.ensureRunning("evento-apac");
                cluster.waitReady("evento-apac", Duration.ofSeconds(420));
            });
            var whoami = gateway.getBody("CINA", "/whoami");
            report.param(LabConfig.regionService("CINA") + " whoami", whoami == null ? "unreachable" : Json.read(whoami));
            var viaLb = whoami != null && whoami.contains("bus-apac");
            report.param("busMode", viaLb ? "stream VIP (BUS_VIA_LB)" : "direct address list");

            var killAt = new AtomicReference<Instant>();
            var killAfter = envSeconds("S03_KILL_AFTER_S", 20);
            var since = Instant.now();
            var records = load(LabConfig.REGIONS, env("S03_RATE", FAULT_RATE), 1, 0.5, envSeconds("S03_DURATION_S", 110),
                    elapsed -> {
                        if (killAt.get() == null && elapsed.compareTo(killAfter) >= 0) {
                            killAt.set(Instant.now());
                            cluster.kill("evento-apac");
                            report.event("killed evento-apac");
                        }
                    });
            var kill = killAt.get();
            if (kill == null) {
                report.harnessError("the kill never happened");
                return;
            }
            var apacAfter = filter(records, r -> r.isCommand() && APAC.contains(r.region())
                    && r.startMs() >= kill.toEpochMilli());
            var firstAck = firstAckAfter(records, APAC, kill.plusSeconds(1));
            var recovery = firstAck == null ? null : (firstAck.startMs() - kill.toEpochMilli()) / 1000.0;
            report.metric("apacFirstAckAfterKillSeconds", recovery);
            report.metric("apacAfterKill", LatencyStats.of(apacAfter));
            report.metric("otherCellsAfterKill", LatencyStats.of(filter(records, r -> r.isCommand()
                    && !APAC.contains(r.region()) && r.startMs() >= kill.toEpochMilli())));

            report.observe("APAC bundles fail over to a healthy server",
                    "APAC commands acknowledged again within 60 s of the kill, through another server",
                    viaLb ? "through the stream VIP backup: reconnect + re-register on evento-eu/am"
                            : "stranded: the bundle redials only the first (dead) address (G2)",
                    recovery == null ? "no APAC command acknowledged while evento-apac was down"
                            : "first APAC ack " + recovery + " s after the kill",
                    recovery != null && recovery <= 60, recovery == null || recovery > 60, null);

            var inFlight = filter(inFlightAt(records, kill), r -> APAC.contains(r.region()));
            var hung = filter(inFlight, r -> r.outcome() != RequestRecord.Outcome.ACK && r.latencyMs() >= 25_000);
            var failedFast = filter(inFlight, r -> r.outcome() != RequestRecord.Outcome.ACK && r.latencyMs() < 25_000);
            report.metric("apacInFlightAtKill", inFlight.size());

            // Where the APAC JVMs are while their server is down: the failover targets.
            var remoteWhileDown = remoteJvms("APAC", APAC_JVMS);
            report.metric("apacRemoteWhileDown", remoteWhileDown);

            report.event("restarting evento-apac");
            cluster.start("evento-apac");
            var back = cluster.waitReady("evento-apac", Duration.ofSeconds(420));
            var backAt = Instant.now();
            report.event("evento-apac ready=" + back);
            cluster.waitAllReady(APAC_JVMS, Duration.ofSeconds(300));

            // N16: the JVMs that failed over must come home by themselves, without a restart,
            // once evento-apac has been healthy for the stability window (30 s + jitter, one more
            // window for the gateways, which handle nothing).
            var stillRemote = awaitFailback("APAC", APAC_JVMS, envSeconds("S03_FAILBACK_WAIT_S", 180));
            var failbackSeconds = stillRemote.isEmpty()
                    ? Duration.between(backAt, Instant.now()).toMillis() / 1000.0 : null;
            report.metric("apacStillRemoteAfterFailbackWait", stillRemote);
            report.metric("apacAllHomeSecondsAfterServerReady", failbackSeconds);
            var failbackMarkers = markers(APAC_JVMS, backAt, List.of(
                    "event=failback_started", "event=failback_completed", "event=failback_failed"));
            report.metric("apacFailbacksCompleted", total(failbackMarkers, "event=failback_completed"));
            report.metric("apacFailbacksFailed", total(failbackMarkers, "event=failback_failed"));
            report.observe("APAC bundles fail back to evento-apac once it is healthy again (N16)",
                    "every APAC JVM that failed over is attached to evento-apac again, without a restart,"
                            + " within the stability window + jitter (+ one window for gateways)",
                    "no failback: the JVMs stay on evento-eu/evento-am until their next disconnect or a"
                            + " restart (N16); 2.4.6 never failed over, so there is nothing to fail back",
                    remoteWhileDown.isEmpty()
                            ? "no APAC JVM was on another server while evento-apac was down"
                            : remoteWhileDown.size() + " JVM(s) failed over " + remoteWhileDown + "; still remote after "
                                    + envSeconds("S03_FAILBACK_WAIT_S", 180).toSeconds() + " s: " + stillRemote
                                    + (failbackSeconds == null ? "" : ", all home " + failbackSeconds
                                    + " s after evento-apac was ready"),
                    remoteWhileDown.isEmpty() ? null : stillRemote.isEmpty(), !stillRemote.isEmpty(), null);

            var serverMarkers = markers(new ArrayList<>(List.of("evento-eu", "evento-am", "evento-apac")), since, MARKERS);
            var appMarkers = markers(APAC_JVMS, since, MARKERS);
            var dupKeys = total(serverMarkers, "duplicate key");
            report.observe("catalog registration under mass reconnect",
                    "no duplicate-key errors while bundles re-register (G8 fix)",
                    "'duplicate key' on core__bundle/component/payload during the reconnect storm (G8)",
                    dupKeys + " duplicate-key log lines on the servers", dupKeys == 0, dupKeys > 0, null);
            report.metric("apacHandshakes", total(appMarkers, "event=handshake_complete"));
            report.metric("apacCorrelationExpired", total(appMarkers, "event=bundle_correlation_expired"));

            // At the fault rate a gateway command is rarely in flight at the kill instant, but the
            // APAC bundles' own calls (saga ProcessPaymentCommand, event fetches) always are. Their
            // fate is in the bundle logs: a typed BrokerChangedException right after the
            // re-handshake (G4 fix) or a request that sits until the 30 s timeout (G4).
            int brokerChanged = 0, bundleTimeouts = 0;
            for (var s : APAC_JVMS) {
                var logs = cluster.logsSince(s, kill);
                for (var line : logs.split("\\R")) {
                    if (!line.startsWith("Caused by")) continue;
                    if (line.contains("BrokerChangedException")) brokerChanged++;
                    if (line.contains("RequestTimeoutException")) bundleTimeouts++;
                }
            }
            report.metric("apacBundleBrokerChanged", brokerChanged);
            report.metric("apacBundleRequestTimeouts", bundleTimeouts);
            report.note("bundle-side in-flight evidence counts 'Caused by' lines in the APAC JVM logs from the kill"
                    + " until the invariant check (" + kill + " onwards)");
            Boolean inFlightOk;
            String inFlightObserved = inFlight.size() + " gateway command(s) in flight: " + hung.size()
                    + " failed after >= 25 s, " + failedFast.size() + " failed fast, kinds " + kinds(inFlight)
                    + "; bundle side: " + brokerChanged + " BrokerChangedException, " + bundleTimeouts
                    + " RequestTimeoutException";
            if (!inFlight.isEmpty()) inFlightOk = hung.isEmpty();
            else if (brokerChanged > 0) inFlightOk = true;
            else if (bundleTimeouts > 0) inFlightOk = false;
            else inFlightOk = null;
            report.observe("in-flight APAC calls at the kill",
                    "fail immediately with a typed broker-changed/disconnect error (G4 fix)",
                    "hang until the 30 s request timeout, outcome indeterminate (G4)",
                    inFlightObserved, inFlightOk, Boolean.FALSE.equals(inFlightOk), null);

            var results = checkInvariants(records, envSeconds("S03_CONVERGENCE_S", 180));
            for (var id : List.of("I1", "I2", "I4", "I5", "I6", "I7")) {
                var inv = find(results, id);
                report.observe(id + " after the server returns", inv.expected(), inv.observed(), inv.passed());
            }
        });
    }
}
