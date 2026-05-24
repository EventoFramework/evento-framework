package com.evento.lab.ms.it;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.lab.ms.api.command.AddOrderItemCommand;
import com.evento.lab.ms.api.command.CreateOrderCommand;
import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.api.event.OrderItemAddedEvent;
import com.evento.lab.ms.api.query.FindOrderByIdQuery;
import com.evento.lab.ms.api.query.ListOrdersQuery;
import com.evento.lab.ms.api.view.OrderView;
import com.evento.lab.ms.command.LabMsCommandApplication;
import com.evento.lab.ms.it.support.MsCommandAwareEmbeddedBroker;
import com.evento.lab.ms.query.LabMsQueryApplication;
import com.evento.lab.ms.query.store.OrderViewStore;
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
 * End-to-end command RTT tests for the ms multi-bundle topology.
 *
 * <p>Architecture under test:
 * <ul>
 *   <li><b>command bundle</b> — hosts {@code OrderAggregate}; handles domain commands.</li>
 *   <li><b>query bundle</b> — hosts {@code OrderProjector} and {@code OrderProjection}.</li>
 *   <li><b>broker</b> — {@link MsCommandAwareEmbeddedBroker} wires {@code CommandBrokerHandler}
 *       with an in-memory {@code MsCommandAwareTestEventStore} that also serves
 *       {@code EventFetchRequest} to the query bundle's consumer engine.</li>
 * </ul>
 *
 * <p>Flow:
 * <ol>
 *   <li>Test sends command via {@code CommandGateway}.</li>
 *   <li>Broker intercepts, fetches aggregate story, forwards decorated command.</li>
 *   <li>Command bundle produces domain event; broker persists it in the in-memory store.</li>
 *   <li>Query bundle's projector polls and receives the event → updates {@code OrderViewStore}.</li>
 *   <li>Test polls {@code OrderViewStore} then queries via {@code QueryGateway}.</li>
 * </ol>
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class MsCommandRttIT {

    private MsCommandAwareEmbeddedBroker broker;
    private MsCommandAwareEmbeddedBroker.TestGatewayClient gatewayClient;
    private EventoBundle commandBundle;
    private EventoBundle queryBundle;

    @BeforeEach
    void setUp() throws Exception {
        OrderViewStore.reset();
        broker = new MsCommandAwareEmbeddedBroker();
    }

    @AfterEach
    void tearDown() throws Exception {
        Duration timeout = Duration.ofSeconds(10);
        if (gatewayClient != null) { gatewayClient.close(); gatewayClient = null; }
        if (commandBundle != null) { commandBundle.getEngineSupervisor().stop(timeout); commandBundle = null; }
        if (queryBundle != null) { queryBundle.getEngineSupervisor().stop(timeout); queryBundle = null; }
        if (broker != null) { broker.close(); broker = null; }
    }

    private void startCommandBundle() throws Exception {
        commandBundle = EventoBundle.Builder.builder()
                .setBasePackage(LabMsCommandApplication.class.getPackage())
                .setBundleId("lab-ms-command")
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .start();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-ms-command"));
    }

    private void startQueryBundle() throws Exception {
        queryBundle = EventoBundle.Builder.builder()
                .setBasePackage(LabMsQueryApplication.class.getPackage())
                .setBundleId("lab-ms-query")
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-ms-query"));
    }

    private void startBundles() throws Exception {
        startCommandBundle();
        startQueryBundle();
        gatewayClient = broker.newGatewayClient();
    }

    // ---- Tests ----

    @Test
    void createOrder_commandToEventToProjection() throws Exception {
        startBundles();

        String orderId = "ms-rtt-create-" + UUID.randomUUID();

        var event = gatewayClient.commandGateway()
                .send(new CreateOrderCommand(orderId, "Ms Test Order", 5), null, null, 30, TimeUnit.SECONDS)
                .get(30, TimeUnit.SECONDS);

        assertThat(event).isInstanceOf(OrderCreatedEvent.class);
        var created = (OrderCreatedEvent) event;
        assertThat(created.getOrderId()).isEqualTo(orderId);
        assertThat(created.getDescription()).isEqualTo("Ms Test Order");
        assertThat(created.getQuantity()).isEqualTo(5);

        // Projector (in query bundle) eventually processes the event
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> OrderViewStore.get(orderId) != null);

        // QueryGateway → OrderProjection verifies the projected view
        Single<OrderView> response = gatewayClient.queryGateway()
                .<Single<OrderView>>query(new FindOrderByIdQuery(orderId), null, null, 15, TimeUnit.SECONDS)
                .get(15, TimeUnit.SECONDS);

        var view = response.getData();
        assertThat(view.getOrderId()).isEqualTo(orderId);
        assertThat(view.getDescription()).isEqualTo("Ms Test Order");
        assertThat(view.getQuantity()).isEqualTo(5);
        assertThat(view.getStatus()).isEqualTo("CREATED");
    }

    @Test
    void addItemsToOrder_aggregateReplayAndProjection() throws Exception {
        startBundles();

        String orderId = "ms-rtt-items-" + UUID.randomUUID();

        // Create order
        gatewayClient.commandGateway()
                .send(new CreateOrderCommand(orderId, "Order With Items", 1), null, null, 30, TimeUnit.SECONDS)
                .get(30, TimeUnit.SECONDS);

        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> OrderViewStore.get(orderId) != null);

        // Add two items — each command replays the aggregate from stored events
        var addItem1 = gatewayClient.commandGateway()
                .send(new AddOrderItemCommand(orderId, "item-A", "Widget A", 9.99, 2), null, null, 30, TimeUnit.SECONDS)
                .get(30, TimeUnit.SECONDS);
        assertThat(addItem1).isInstanceOf(OrderItemAddedEvent.class);

        var addItem2 = gatewayClient.commandGateway()
                .send(new AddOrderItemCommand(orderId, "item-B", "Widget B", 4.99, 1), null, null, 30, TimeUnit.SECONDS)
                .get(30, TimeUnit.SECONDS);
        assertThat(addItem2).isInstanceOf(OrderItemAddedEvent.class);

        // Projector processes both item-added events
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> {
                    var v = OrderViewStore.get(orderId);
                    return v != null && v.getItems() != null && v.getItems().size() == 2;
                });

        var view = OrderViewStore.get(orderId);
        assertThat(view.getItems()).hasSize(2);
        assertThat(view.getItems()).extracting("itemId")
                .containsExactlyInAnyOrder("item-A", "item-B");
    }

    @Test
    void listOrders_multipleOrdersAllProjected() throws Exception {
        startBundles();

        String[] orderIds = {
                "ms-rtt-list-" + UUID.randomUUID(),
                "ms-rtt-list-" + UUID.randomUUID(),
                "ms-rtt-list-" + UUID.randomUUID()
        };

        for (int i = 0; i < orderIds.length; i++) {
            gatewayClient.commandGateway()
                    .send(new CreateOrderCommand(orderIds[i], "List Order " + i, i + 1), null, null, 30, TimeUnit.SECONDS)
                    .get(30, TimeUnit.SECONDS);
        }

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> {
                    for (String id : orderIds) {
                        if (OrderViewStore.get(id) == null) return false;
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
