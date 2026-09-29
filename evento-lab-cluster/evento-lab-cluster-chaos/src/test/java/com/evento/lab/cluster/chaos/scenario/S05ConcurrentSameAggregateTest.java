package com.evento.lab.cluster.chaos.scenario;

import com.evento.lab.cluster.chaos.LabConfig;
import com.evento.lab.cluster.chaos.LatencyStats;
import com.evento.lab.cluster.chaos.RequestRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * S5: EUROPA and CINA hammer {@code AddOrderItem} on the same few orders at
 * once, with the WAN baseline on (EU-APAC 150 ms RTT). Two servers then take
 * {@code pg_advisory_lock(RESOURCE:<id>)} on the same key through one pinned
 * connection each (G3): expect head-of-line stalls up to the 30 s socket
 * timeout, possibly a cross-server lock deadlock. The aggregate must not fork
 * (I3). {@code pg_locks} is sampled on the ES primary while it runs.
 */
@EnabledIfEnvironmentVariable(named = "EVENTO_LAB_CLUSTER", matches = "true")
class S05ConcurrentSameAggregateTest extends AbstractScenario {

    @Override
    protected String id() {
        return "S05";
    }

    @Override
    protected String title() {
        return "Concurrent same-aggregate commands from two cells";
    }

    @Override
    protected List<String> gaps() {
        return List.of("G3", "G6", "G9");
    }

    @Test
    void concurrentSameAggregate() throws Exception {
        scenario(() -> {
            var hot = LabConfig.envInt("S05_HOT_ORDERS", 2);
            var perWorker = LabConfig.envInt("S05_COMMANDS_PER_WORKER", 30);
            var workersPerRegion = LabConfig.envInt("S05_WORKERS_PER_REGION", 2);
            var regions = List.of("EUROPA", "CINA");
            var wan = toxi.towards("es-pg-eu").stream()
                    .map(p -> p.name() + "=" + p.toxics().stream().map(t -> t.name() + t.attributes()).toList())
                    .toList();
            report.param("wanTowardsEsPrimary", wan);

            var orders = new ArrayList<String>();
            for (int i = 0; i < hot; i++) {
                var id = gateway.newOrderId(prefix + "hot-", "EUROPA");
                var r = gateway.create("EUROPA", id, UUID.randomUUID().toString());
                if (r.outcome() == RequestRecord.Outcome.ACK) orders.add(id);
            }
            if (orders.isEmpty()) {
                report.harnessError("could not create the hot orders");
                return;
            }
            report.event("hot orders " + orders);

            var records = Collections.synchronizedList(new ArrayList<RequestRecord>());
            var lockSamples = Collections.synchronizedList(new ArrayList<String>());
            var done = new AtomicBoolean(false);
            var sampler = Thread.ofVirtual().start(() -> {
                while (!done.get()) {
                    var e = cluster.psql("es-pg-eu", "evento",
                            "SELECT (SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND granted) || '/' || "
                                    + "(SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted) || '/' || "
                                    + "(SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock')");
                    lockSamples.add(java.time.Instant.now() + " granted/waiting/lockWaiters=" + e.out().trim());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
            });
            report.event("contention start: " + regions + " x " + workersPerRegion + " workers x " + perWorker);
            try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
                for (var region : regions) {
                    for (int w = 0; w < workersPerRegion; w++) {
                        exec.submit(() -> {
                            for (int i = 0; i < perWorker; i++) {
                                var order = orders.get(i % orders.size());
                                records.add(gateway.addItem(region, order, "sku-" + region, 1,
                                        UUID.randomUUID().toString()));
                            }
                        });
                    }
                }
            }
            done.set(true);
            sampler.interrupt();
            report.event("contention done: " + records.size() + " commands");
            report.attach("pg_locks samples (es-pg-eu)", lockSamples);

            var byRegion = LatencyStats.byRegion(records);
            report.metric("latencyByRegion", byRegion);
            var all = LatencyStats.of(records);
            report.metric("latencyAll", all);
            var maxWaiting = lockSamples.stream().mapToInt(s -> {
                try {
                    return Integer.parseInt(s.substring(s.indexOf('=') + 1).split("/")[1]);
                } catch (Exception e) {
                    return 0;
                }
            }).max().orElse(0);
            report.metric("maxAdvisoryLockWaiters", maxWaiting);

            report.observe("contended command latency",
                    "bounded by lock hand-off + WAN RTT: p99 well under 5 s",
                    "p99 ~ 30 s stalls (one pinned lock connection per server, G3), possibly a cross-server deadlock",
                    all.toString(), all.p99() < 5000, all.p99() >= 25_000 || all.kinds().containsKey("timeout"),
                    null);

            var results = checkInvariants(records, envSeconds("S05_CONVERGENCE_S", 120));
            var i3 = find(results, "I3");
            report.observe("no aggregate fork (I3)", i3.expected(),
                    "a lost advisory lock lets two servers append from one story (G3 + G6)",
                    i3.observed(), i3.passed(), !i3.passed(), null);
            var i1 = find(results, "I1");
            var i2 = find(results, "I2");
            report.observe("items stored = items acknowledged (I1, I2)", "no loss, no duplicate",
                    i1.observed() + " | " + i2.observed(), i1.passed() && i2.passed());
        });
    }
}
