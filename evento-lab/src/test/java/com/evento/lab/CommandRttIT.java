package com.evento.lab;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.v2.ConsumerEngineConfig;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.api.command.CancelOrderCommand;
import com.evento.lab.api.command.ConfirmOrderCommand;
import com.evento.lab.api.command.CreateOrderCommand;
import com.evento.lab.api.event.OrderCancelledEvent;
import com.evento.lab.api.event.OrderConfirmedEvent;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.api.query.FindOrderByIdQuery;
import com.evento.lab.api.query.ListOrdersQuery;
import com.evento.lab.api.view.OrderView;
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
 * End-to-end command round-trip tests that exercise the full
 * Aggregate → EventStore → Projector → Projection chain through the v2 broker.
 *
 * <p>Flow under test:
 * <ol>
 *   <li>Test sends a command via {@code CommandGateway} to the broker.</li>
 *   <li>{@code CommandBrokerHandler} intercepts, fetches aggregate story from the
 *       in-memory store, forwards a {@code DecoratedDomainCommandMessage} to the bundle.</li>
 *   <li>Bundle's {@code LabAggregate} / {@code LabService} produces the response event.</li>
 *   <li>Broker stores the event and returns the {@code DomainEventMessage} to the caller.</li>
 *   <li>Projector consumer engine picks up the event and updates {@code LabStore}.</li>
 *   <li>Test queries via {@code QueryGateway} and verifies the projected view.</li>
 * </ol>
 *
 * <p>No Docker or real database required — all state is in-memory.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class CommandRttIT {

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

    /** Starts a bundle that includes Aggregate + Service + Projector + Projection components. */
    private void startFullBundle() throws Exception {
        // LabStore.class.getPackage() → com.evento.lab.bundle which covers all sub-packages
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(LabStore.class.getPackage())
                .setBundleId("lab-bundle")
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-bundle"));
        gatewayClient = broker.newGatewayClient();
    }

    // ---- Tests ----

    @Test
    void createOrder_aggregateStoresEventAndProjectorWritesView() throws Exception {
        startFullBundle();

        String orderId = "rtt-create-" + UUID.randomUUID();
        var event = gatewayClient.commandGateway()
                .send(new CreateOrderCommand(orderId, "Test Order", 3), null, null, 30, TimeUnit.SECONDS)
                .get(30, TimeUnit.SECONDS);

        assertThat(event).isInstanceOf(OrderCreatedEvent.class);
        var created = (OrderCreatedEvent) event;
        assertThat(created.getOrderId()).isEqualTo(orderId);
        assertThat(created.getDescription()).isEqualTo("Test Order");
        assertThat(created.getQuantity()).isEqualTo(3);

        // Projector eventually writes the view (eventual consistency)
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> LabStore.get(orderId) != null);

        // Query via QueryGateway verifies the projection
        Single<OrderView> response = gatewayClient.queryGateway()
                .<Single<OrderView>>query(new FindOrderByIdQuery(orderId), null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);

        var view = response.getData();
        assertThat(view.getOrderId()).isEqualTo(orderId);
        assertThat(view.getDescription()).isEqualTo("Test Order");
        assertThat(view.getQuantity()).isEqualTo(3);
        assertThat(view.getStatus()).isEqualTo("CREATED");
    }

    @Test
    void confirmOrder_serviceCommandWithLock_projectorUpdatesStatus() throws Exception {
        startFullBundle();

        String orderId = "rtt-confirm-" + UUID.randomUUID();

        // Create order via aggregate command
        gatewayClient.commandGateway()
                .send(new CreateOrderCommand(orderId, "Confirm Me", 1), null, null, 30, TimeUnit.SECONDS)
                .get(30, TimeUnit.SECONDS);

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> LabStore.get(orderId) != null);

        // Confirm order via service command (has lockId — exercises JVM-only lock path)
        var confirmEvent = gatewayClient.commandGateway()
                .send(new ConfirmOrderCommand(orderId), null, null, 30, TimeUnit.SECONDS)
                .get(30, TimeUnit.SECONDS);

        assertThat(confirmEvent).isInstanceOf(OrderConfirmedEvent.class);

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> "CONFIRMED".equals(LabStore.get(orderId).getStatus()));

        assertThat(LabStore.get(orderId).getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void cancelOrder_serviceCommandWithLock_projectorMarksAsCancelled() throws Exception {
        startFullBundle();

        String orderId = "rtt-cancel-" + UUID.randomUUID();

        gatewayClient.commandGateway()
                .send(new CreateOrderCommand(orderId, "Cancel Me", 2), null, null, 30, TimeUnit.SECONDS)
                .get(30, TimeUnit.SECONDS);

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> LabStore.get(orderId) != null);

        var cancelEvent = gatewayClient.commandGateway()
                .send(new CancelOrderCommand(orderId), null, null, 30, TimeUnit.SECONDS)
                .get(30, TimeUnit.SECONDS);

        assertThat(cancelEvent).isInstanceOf(OrderCancelledEvent.class);

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> LabStore.get(orderId) != null && LabStore.get(orderId).isCancelled());

        var view = LabStore.get(orderId);
        assertThat(view.getStatus()).isEqualTo("CANCELLED");
        assertThat(view.isCancelled()).isTrue();
    }

    @Test
    void listOrders_queryReturnsAllProjectedOrders() throws Exception {
        startFullBundle();

        String[] orderIds = {
                "rtt-list-" + UUID.randomUUID(),
                "rtt-list-" + UUID.randomUUID(),
                "rtt-list-" + UUID.randomUUID()
        };

        for (int i = 0; i < orderIds.length; i++) {
            gatewayClient.commandGateway()
                    .send(new CreateOrderCommand(orderIds[i], "Order " + i, i + 1), null, null, 30, TimeUnit.SECONDS)
                    .get(30, TimeUnit.SECONDS);
        }

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> {
                    for (String id : orderIds) {
                        if (LabStore.get(id) == null) return false;
                    }
                    return true;
                });

        Multiple<OrderView> listResp = gatewayClient.queryGateway()
                .<Multiple<OrderView>>query(new ListOrdersQuery(), null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);

        var views = listResp.getData();
        for (String id : orderIds) {
            assertThat(views.stream().anyMatch(v -> v.getOrderId().equals(id)))
                    .as("order %s should appear in ListOrdersQuery result", id)
                    .isTrue();
        }
    }
}
