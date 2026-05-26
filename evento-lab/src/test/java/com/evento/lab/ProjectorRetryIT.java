package com.evento.lab;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.lab.api.command.CreateOrderCommand;
import com.evento.lab.api.command.UpdateOrderCommand;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.support.CommandAwareEmbeddedBroker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies projector retry semantics under real event-store conditions.
 *
 * <ul>
 *   <li>{@code LabFailProjector} declares {@code @EventHandler(retry=3)} on
 *       {@code OrderUpdatedEvent}. It fails the first three attempts for each
 *       event sequence number, then succeeds.  The bundle must survive all four
 *       delivery attempts and remain operational.</li>
 *   <li>{@code LabAlwaysFailProjector} declares {@code @EventHandler(retry=0)},
 *       meaning the event goes straight to the dead-letter queue on the first
 *       failure.  {@code LabProjector} (which also handles the same event) must
 *       still process it, and the bundle must stay alive.</li>
 * </ul>
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class ProjectorRetryIT {

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
        if (bundle != null) { bundle.getEngineSupervisor().stop(Duration.ofSeconds(10)); bundle = null; }
        if (broker != null) { broker.close(); broker = null; }
    }

    private void startBundle() throws Exception {
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(LabStore.class.getPackage())
                .setBundleId("lab-retry-bundle")
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-retry-bundle"));
        gatewayClient = broker.newGatewayClient();
    }

    private String createAndAwaitOrder(String description) throws Exception {
        String orderId = "retry-" + UUID.randomUUID();
        gatewayClient.commandGateway()
                .send(new CreateOrderCommand(orderId, description, 1), null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);
        await().atMost(10, TimeUnit.SECONDS).until(() -> LabStore.get(orderId) != null);
        return orderId;
    }

    @Test
    void failProjectorRetriesAndBundleStaysAlive() throws Exception {
        startBundle();
        String orderId = createAndAwaitOrder("original");

        // UpdateOrderCommand triggers OrderUpdatedEvent.
        // LabFailProjector(retry=3) will fail attempts 1-3 then succeed on attempt 4.
        // LabProjector(retry=3) succeeds on the first attempt and updates LabStore.
        gatewayClient.commandGateway()
                .send(new UpdateOrderCommand(orderId, "updated-by-retry-test", 9),
                        null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);

        // LabProjector processed the update — description and quantity are visible.
        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    var v = LabStore.get(orderId);
                    return v != null && "updated-by-retry-test".equals(v.getDescription());
                });

        assertThat(LabStore.get(orderId).getQuantity()).isEqualTo(9);
        assertThat(bundle.getEngineSupervisor().isShuttingDown()).isFalse();
    }

    @Test
    void alwaysFailProjectorDlqAndBundleStaysAlive() throws Exception {
        startBundle();
        String orderId = createAndAwaitOrder("original");

        // OrderUpdatedEvent also goes to LabAlwaysFailProjector(retry=0).
        // It always throws → event moved to DLQ immediately, no retries.
        // LabProjector still processes the same event successfully.
        gatewayClient.commandGateway()
                .send(new UpdateOrderCommand(orderId, "updated-by-dlq-test", 7),
                        null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);

        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    var v = LabStore.get(orderId);
                    return v != null && "updated-by-dlq-test".equals(v.getDescription());
                });

        assertThat(LabStore.get(orderId).getQuantity()).isEqualTo(7);
        assertThat(bundle.getEngineSupervisor().isShuttingDown()).isFalse();
    }
}
