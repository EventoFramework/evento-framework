package com.evento.lab;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.lab.api.command.CreateOrderCommand;
import com.evento.lab.api.command.LabObserverFailCommand;
import com.evento.lab.api.command.LabSagaFailCommand;
import com.evento.lab.api.command.LabTimeoutCommand;
import com.evento.lab.api.command.UpdateOrderCommand;
import com.evento.lab.api.event.LabObserverFailEvent;
import com.evento.lab.api.event.LabSagaFailEvent;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.api.query.FindOrderByIdQuery;
import com.evento.lab.bundle.LabBundleInterceptor;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.support.CommandAwareEmbeddedBroker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end matrix covering every failure stage across the full command / query RTT pipeline.
 *
 * <p>Each test exercises one of:
 * <ul>
 *   <li>Interceptor throwing BEFORE the aggregate command handler</li>
 *   <li>Interceptor throwing AFTER the aggregate command handler</li>
 *   <li>Interceptor throwing BEFORE the service command handler</li>
 *   <li>Interceptor throwing AFTER the service command handler</li>
 *   <li>Interceptor throwing BEFORE the query handler</li>
 *   <li>Interceptor throwing AFTER the query handler</li>
 *   <li>Saga handler failure (bundle survives)</li>
 *   <li>Observer handler failure (bundle survives)</li>
 *   <li>Slow command handler exceeds caller timeout</li>
 * </ul>
 *
 * <p>Failure is triggered by flags on the payload itself so each test controls
 * which stage it wants to fail without global state.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class FailureMatrixIT {

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
                .setBundleId("lab-failure-bundle")
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .setMessageHandlerInterceptor(new LabBundleInterceptor())
                .start();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-failure-bundle"));
        gatewayClient = broker.newGatewayClient();
    }

    private String createAndAwaitOrder() throws Exception {
        String orderId = "fm-" + UUID.randomUUID();
        gatewayClient.commandGateway()
                .send(new CreateOrderCommand(orderId, "desc", 1), null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);
        await().atMost(10, TimeUnit.SECONDS).until(() -> LabStore.get(orderId) != null);
        return orderId;
    }

    // ── Aggregate command interceptor ──────────────────────────────────────

    @Test
    void interceptorFailsBeforeAggregateCommand() throws Exception {
        startBundle();
        String orderId = createAndAwaitOrder();

        var cmd = new UpdateOrderCommand(orderId, "new-desc", 5);
        cmd.setFailBeforeHandling(true);
        var future = gatewayClient.commandGateway().send(cmd, null, null, 15, TimeUnit.SECONDS);

        assertThatThrownBy(() -> future.get(15, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .hasMessageContaining("BEFORE aggregate command handling");

        // Verify the order was NOT updated (handler did not run)
        assertThat(LabStore.get(orderId).getDescription()).isEqualTo("desc");
    }

    @Test
    void interceptorFailsAfterAggregateCommand() throws Exception {
        startBundle();
        String orderId = createAndAwaitOrder();

        var cmd = new UpdateOrderCommand(orderId, "new-desc", 5);
        cmd.setFailAfterHandling(true);
        var future = gatewayClient.commandGateway().send(cmd, null, null, 15, TimeUnit.SECONDS);

        assertThatThrownBy(() -> future.get(15, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .hasMessageContaining("AFTER aggregate command handling");
    }

    // ── Service command interceptor ────────────────────────────────────────

    @Test
    void interceptorFailsBeforeServiceCommand() throws Exception {
        startBundle();
        var cmd = new LabTimeoutCommand(0L, 1L);
        cmd.setFailBeforeHandling(true);
        var future = gatewayClient.commandGateway().send(cmd, null, null, 15, TimeUnit.SECONDS);

        assertThatThrownBy(() -> future.get(15, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .hasMessageContaining("BEFORE service command handling");
    }

    @Test
    void interceptorFailsAfterServiceCommand() throws Exception {
        startBundle();
        var cmd = new LabTimeoutCommand(0L, 1L);
        cmd.setFailAfterHandling(true);
        var future = gatewayClient.commandGateway().send(cmd, null, null, 15, TimeUnit.SECONDS);

        assertThatThrownBy(() -> future.get(15, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .hasMessageContaining("AFTER service command handling");
    }

    // ── Query interceptor ──────────────────────────────────────────────────

    @Test
    void interceptorFailsBeforeQuery() throws Exception {
        startBundle();
        String orderId = createAndAwaitOrder();

        var q = new FindOrderByIdQuery(orderId);
        q.setFailBeforeHandling(true);
        var future = gatewayClient.queryGateway().query(q, null, null, 15, TimeUnit.SECONDS);

        assertThatThrownBy(() -> future.get(15, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .hasMessageContaining("BEFORE query handling");
    }

    @Test
    void interceptorFailsAfterQuery() throws Exception {
        startBundle();
        String orderId = createAndAwaitOrder();

        var q = new FindOrderByIdQuery(orderId);
        q.setFailAfterHandling(true);
        var future = gatewayClient.queryGateway().query(q, null, null, 15, TimeUnit.SECONDS);

        assertThatThrownBy(() -> future.get(15, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .hasMessageContaining("AFTER query handling");
    }

    // ── Consumer handler failures (bundle-survival checks) ─────────────────

    @Test
    void sagaHandlerFailureDoesNotCrashBundle() throws Exception {
        startBundle();

        // Trigger saga handler failure: the service command completes fine,
        // but LabSaga.on(LabSagaFailEvent) throws.
        var result = gatewayClient.commandGateway()
                .send(new LabSagaFailCommand(), null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);
        assertThat(result).isInstanceOf(LabSagaFailEvent.class);

        // Bundle must still serve commands after the async consumer failure.
        var followUp = gatewayClient.commandGateway()
                .send(new CreateOrderCommand("saga-fail-followup-" + UUID.randomUUID(), "d", 1),
                        null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);
        assertThat(followUp).isInstanceOf(OrderCreatedEvent.class);
        assertThat(bundle.getEngineSupervisor().isShuttingDown()).isFalse();
    }

    @Test
    void observerHandlerFailureDoesNotCrashBundle() throws Exception {
        startBundle();

        // Trigger observer handler failure.
        var result = gatewayClient.commandGateway()
                .send(new LabObserverFailCommand(), null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);
        assertThat(result).isInstanceOf(LabObserverFailEvent.class);

        // Bundle still processes commands after the async observer failure.
        var followUp = gatewayClient.commandGateway()
                .send(new CreateOrderCommand("obs-fail-followup-" + UUID.randomUUID(), "d", 1),
                        null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);
        assertThat(followUp).isInstanceOf(OrderCreatedEvent.class);
        assertThat(bundle.getEngineSupervisor().isShuttingDown()).isFalse();
    }

    // ── Timeout ───────────────────────────────────────────────────────────

    @Test
    void slowHandlerExceedsCallerTimeout() throws Exception {
        startBundle();

        // Handler sleeps 5 s; caller waits only 1 s.
        var future = gatewayClient.commandGateway()
                .send(new LabTimeoutCommand(5_000L, 1L), null, null, 30, TimeUnit.SECONDS);

        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(TimeoutException.class);

        // Bundle remains operational after the caller timed out.
        assertThat(bundle.getEngineSupervisor().isShuttingDown()).isFalse();
    }
}
