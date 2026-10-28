package com.evento.lab.multievent;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.admin.AdminPayloadCodec;
import com.evento.common.modeling.messaging.message.internal.discovery.RegisteredHandler;
import com.evento.common.performance.PerformanceInvocationsMessage;
import com.evento.lab.support.CommandAwareEmbeddedBroker;
import com.evento.server.bus.event.BusEvent;
import com.evento.transport.protocol.ProtocolPayloadTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Several events from one command (3.0, 3.0.1) end to end, through the broker over the socket:
 * what the caller is answered, what is stored, what the consumers see, and what the catalog and
 * the telemetry tell the server's performance model: every event a handler declares, and how
 * many of each one execution produced, 0 included (a coupon used only sometimes, a service that
 * may answer without an event).
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class MultiEventCommandIT {

    private static final String BUNDLE_ID = "multi-event-bundle";

    private CommandAwareEmbeddedBroker broker;
    private CommandAwareEmbeddedBroker.TestGatewayClient caller;
    private EventoBundle bundle;
    private final List<RegisteredHandler> discovered = new CopyOnWriteArrayList<>();
    private final List<PerformanceInvocationsMessage> telemetry = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        BasketProjector.reset();
        broker = new CommandAwareEmbeddedBroker();
        var codec = new AdminPayloadCodec();
        broker.eventBus().subscribe(e -> {
            if (e instanceof BusEvent.BundleDiscovered d && BUNDLE_ID.equals(d.node().bundleId())) {
                discovered.addAll(d.discovery().handlers());
            }
            if (e instanceof BusEvent.AdminNotification an
                    && ProtocolPayloadTypes.BUNDLE_ADMIN_NOTIFICATION.equals(an.payloadType())
                    && codec.decodeMessage(an.payload()).getBody() instanceof PerformanceInvocationsMessage pi) {
                telemetry.add(pi);
            }
        });
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(BasketAggregate.class.getPackage())
                .setBundleId(BUNDLE_ID)
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
        await().atMost(20, TimeUnit.SECONDS).until(() -> broker.lifecycle().isBundleAvailable(BUNDLE_ID));
        caller = broker.newGatewayClient();
        caller.commandGateway().send(new CreateBasket("b-1")).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (caller != null) caller.close();
        if (bundle != null) bundle.getEngineSupervisor().stop(Duration.ofSeconds(10));
        if (broker != null) broker.close();
    }

    @Test
    void theCallerGetsTheLastEventAndAllAreStoredInOrderAndConsumed() throws Exception {
        Object answer = caller.commandGateway().send(new CheckOut("b-1", 2, true)).get(30, TimeUnit.SECONDS);

        assertThat(answer).isInstanceOf(CouponUsed.class);
        var stored = broker.eventStore().allEvents().stream()
                .filter(e -> "b-1".equals(e.getAggregateId()))
                .toList();
        assertThat(stored).extracting(e -> e.getEventName()).containsExactly(
                "BasketCreated", "ItemReserved", "ItemReserved", "BasketCheckedOut", "CouponUsed");
        // consecutive: nothing of another command between them
        var numbers = stored.stream().skip(1).map(e -> e.getEventSequenceNumber()).toList();
        for (int i = 1; i < numbers.size(); i++) assertThat(numbers.get(i)).isGreaterThan(numbers.get(i - 1));
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertThat(BasketProjector.seen).containsExactly(
                "ItemReserved:b-1:0", "ItemReserved:b-1:1", "BasketCheckedOut:b-1", "CouponUsed:b-1"));
    }

    @Test
    void anEventOutsideTheDeclarationFailsTheCommandAndNothingIsStored() {
        var before = broker.eventStore().allEvents().size();

        assertThat(caller.commandGateway().send(new Sneak("b-1"))).failsWithin(30, TimeUnit.SECONDS)
                .withThrowableThat().withMessageContaining("BasketCheckedOut").withMessageContaining("produces");
        assertThat(broker.eventStore().allEvents()).hasSize(before);
    }

    @Test
    void theCatalogListsEveryDeclaredEventOfEachCommandHandler() {
        await().atMost(20, TimeUnit.SECONDS).until(() -> discovered.stream()
                .anyMatch(h -> "CheckOut".equals(h.getHandledPayload())));

        var checkOut = handler("CheckOut");
        assertThat(checkOut.getReturnType()).isEqualTo("ItemReserved");   // what an older broker reads
        assertThat(checkOut.getProducedEvents())
                .containsExactlyInAnyOrder("ItemReserved", "BasketCheckedOut", "CouponUsed");
        assertThat(handler("CreateBasket").getProducedEvents()).containsExactly("BasketCreated");
        assertThat(handler("Notify").getProducedEvents()).containsExactly("Notified");
        assertThat(handler("ItemReserved").getProducedEvents()).isEmpty();   // a projector produces none
    }

    @Test
    void theTelemetryCountsEachDeclaredEventPerCommandZeroIncluded() throws Exception {
        caller.commandGateway().send(new CheckOut("b-1", 3, false)).get(30, TimeUnit.SECONDS);
        caller.commandGateway().send(new Notify("n-1", true)).get(30, TimeUnit.SECONDS);
        caller.commandGateway().send(new Notify("n-2", false)).get(30, TimeUnit.SECONDS);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(produced("CheckOut")).containsExactly(
                    Map.of("ItemReserved", 3, "BasketCheckedOut", 1, "CouponUsed", 0));
            assertThat(produced("Notify")).containsExactlyInAnyOrder(
                    Map.of("Notified", 0), Map.of("Notified", 1));
            assertThat(produced("CreateBasket")).containsExactly(Map.of("BasketCreated", 1));
        });
    }

    private RegisteredHandler handler(String payload) {
        return discovered.stream().filter(h -> payload.equals(h.getHandledPayload())).findFirst().orElseThrow();
    }

    private List<Map<String, Integer>> produced(String action) {
        return telemetry.stream().filter(t -> action.equals(t.getAction()))
                .map(t -> (Map<String, Integer>) t.getProducedEvents()).toList();
    }
}
