package com.evento.lab;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.lab.api.command.StressAggregateCallCommand;
import com.evento.lab.api.command.StressAggregateCreateCommand;
import com.evento.lab.api.command.StressServiceCallCommand;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.support.CommandAwareEmbeddedBroker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Throughput / concurrency smoke tests.
 *
 * <p>Tests verify that the framework handles concurrent load without dropping
 * commands, deadlocking, or corrupting state.  They are not precision
 * benchmarks — the goal is correctness under parallelism, not raw throughput.
 *
 * <ul>
 *   <li>{@code concurrentServiceCommandsAllProcessed} — 30 parallel
 *       {@link StressServiceCallCommand} (each also issues an internal
 *       {@code ListOrdersQuery}).  All 30 must produce a
 *       {@code StressServiceCalledEvent} that {@code LabStressProjector}
 *       records.</li>
 *   <li>{@code aggregateCommandsSerializedAndAllProcessed} — 10 sequential
 *       {@link StressAggregateCallCommand} on the same aggregate (aggregate
 *       locking enforces serialisation).  All 10 counters must be recorded
 *       by {@code LabStressProjector}.</li>
 * </ul>
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class StressIT {

    private static final int SERVICE_CONCURRENCY = 30;
    private static final int AGGREGATE_CALLS     = 10;

    private CommandAwareEmbeddedBroker broker;
    private CommandAwareEmbeddedBroker.TestGatewayClient gatewayClient;
    private EventoBundle bundle;

    @BeforeEach
    void setUp() throws Exception {
        LabStore.reset();
        broker = new CommandAwareEmbeddedBroker();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (gatewayClient != null) { gatewayClient.close(); gatewayClient = null; }
        if (bundle != null) { bundle.getEngineSupervisor().stop(Duration.ofSeconds(15)); bundle = null; }
        if (broker != null) { broker.close(); broker = null; }
    }

    private void startBundle() throws Exception {
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(LabStore.class.getPackage())
                .setBundleId("lab-stress-bundle")
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-stress-bundle"));
        gatewayClient = broker.newGatewayClient();
    }

    @Test
    void concurrentServiceCommandsAllProcessed() throws Exception {
        startBundle();
        String stressId = "svc-" + UUID.randomUUID();

        // Fire all commands in parallel using virtual threads.
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<CompletableFuture<Void>>();
            for (int i = 0; i < SERVICE_CONCURRENCY; i++) {
                final int instance = i;
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        gatewayClient.commandGateway()
                                .send(new StressServiceCallCommand(stressId, instance),
                                        null, null, 30, TimeUnit.SECONDS)
                                .get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException("stress command " + instance + " failed", e);
                    }
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);
        }

        // LabStressProjector must have recorded all SERVICE_CONCURRENCY counters.
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            for (int i = 0; i < SERVICE_CONCURRENCY; i++) {
                if (LabStore.getStressCount(stressId + "_" + i) < 1) return false;
            }
            return true;
        });

        for (int i = 0; i < SERVICE_CONCURRENCY; i++) {
            assertThat(LabStore.getStressCount(stressId + "_" + i))
                    .as("counter for instance %d", i)
                    .isGreaterThanOrEqualTo(1L);
        }
    }

    @Test
    void aggregateCommandsSerializedAndAllProcessed() throws Exception {
        startBundle();
        String stressId = "agg-" + UUID.randomUUID();

        // Create the stress aggregate first (init command).
        gatewayClient.commandGateway()
                .send(new StressAggregateCreateCommand(stressId, AGGREGATE_CALLS),
                        null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);

        // Fire AGGREGATE_CALLS sequential calls on the same aggregate.
        // (Aggregate locking serialises them; they still all must complete.)
        var callFutures = new ArrayList<CompletableFuture<Void>>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < AGGREGATE_CALLS; i++) {
                final int instance = i;
                callFutures.add(CompletableFuture.runAsync(() -> {
                    try {
                        gatewayClient.commandGateway()
                                .send(new StressAggregateCallCommand(stressId, instance),
                                        null, null, 30, TimeUnit.SECONDS)
                                .get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException("agg call " + instance + " failed", e);
                    }
                }, pool));
            }
            CompletableFuture.allOf(callFutures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);
        }

        // LabStressProjector must have recorded all AGGREGATE_CALLS counters.
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            for (int i = 0; i < AGGREGATE_CALLS; i++) {
                if (LabStore.getStressCount(stressId + "_" + i) < 1) return false;
            }
            return true;
        });

        for (int i = 0; i < AGGREGATE_CALLS; i++) {
            assertThat(LabStore.getStressCount(stressId + "_" + i))
                    .as("counter for aggregate call %d", i)
                    .isGreaterThanOrEqualTo(1L);
        }
        assertThat(bundle.getEngineSupervisor().isShuttingDown()).isFalse();
    }
}
