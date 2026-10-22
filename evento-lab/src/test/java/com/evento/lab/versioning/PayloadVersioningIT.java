package com.evento.lab.versioning;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.modeling.common.SerializedObject;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.common.modeling.state.SerializedAggregateState;
import com.evento.common.versioning.Upcaster;
import com.evento.lab.support.CommandAwareEmbeddedBroker;
import com.evento.lab.legacyclients.FindItem;
import com.evento.lab.legacyclients.RenameItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Payload versioning end to end (3.0): a history written by the previous version of a bundle
 * (events at revision 0, an event class since replaced by a V2 class, a snapshot of the old
 * state) is read by the current version, whose classes know only the current shapes, through
 * its upcasters, on every read path: a projector, an aggregate rebuilt from events, an aggregate
 * rebuilt from a snapshot. Callers not yet upgraded keep sending the old command and query, and
 * reach the renamed handlers.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class PayloadVersioningIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The history of this bundle's payloads, as its developers would register it. */
    private static final Upcaster[] HISTORY = {
            Upcaster.from(ItemAdded.class, 0).to(1, json -> {
                json.set("title", json.remove("name"));
                json.put("quantity", 1);
            }),
            Upcaster.from("ItemRenamed", 0).to(ItemRenamedV2.class, 0, json -> json.put("reason", "legacy")),
            Upcaster.from("RenameItem", 0).to(RenameItemV2.class, 0, json -> json.set("title", json.remove("newTitle"))),
            Upcaster.from("FindItem", 0).to(FindItemV2.class, 0, json -> json.set("itemId", json.remove("id"))),
            Upcaster.from(ItemState.class, 0).to(1, json -> json.set("title", json.remove("name"))),
    };

    private CommandAwareEmbeddedBroker broker;
    private CommandAwareEmbeddedBroker.TestGatewayClient caller;
    private EventoBundle bundle;

    @BeforeEach
    void setUp() throws Exception {
        ItemStore.reset();
        broker = new CommandAwareEmbeddedBroker();
        var store = broker.eventStore();
        // Written by the previous version of the bundle.
        store.publishEvent(legacyEvent(new ItemAdded("i-1", "pen", 0), null,
                json -> {
                    json.set("name", json.remove("title"));
                    json.remove("quantity");
                }), "i-1");
        store.publishEvent(legacyEvent(new ItemRenamedV2("i-1", "pencil", null), "ItemRenamed",
                json -> json.remove("reason")), "i-1");
        store.saveSnapshot("i-2", 0L, legacy(new SerializedAggregateState<>(new ItemState("cup")), null,
                json -> json.set("name", json.remove("title"))));

        bundle = EventoBundle.Builder.builder()
                .setBasePackage(ItemAggregate.class.getPackage())
                .setBundleId("versioning-bundle")
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .addUpcasters(HISTORY)
                .start();
        await().atMost(20, TimeUnit.SECONDS).until(() -> broker.lifecycle().isBundleAvailable("versioning-bundle"));
        caller = broker.newGatewayClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (caller != null) caller.close();
        if (bundle != null) bundle.getEngineSupervisor().stop(Duration.ofSeconds(10));
        if (broker != null) broker.close();
    }

    @Test
    void aProjectorReadsTheOldHistoryInTheCurrentShape() {
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            var item = ItemStore.items.get("i-1");
            assertThat(item).isNotNull();
            assertThat(item.getTitle()).isEqualTo("pencil");    // ItemRenamed, read as ItemRenamedV2
            assertThat(item.getQuantity()).isEqualTo(1);        // ItemAdded revision 0 had none
        });
        assertThat(ItemStore.renames).containsExactly("i-1:legacy");
    }

    @Test
    void anAggregateIsRebuiltFromTheOldHistoryInTheCurrentShape() throws Exception {
        ItemRenamedV2 fromEvents = caller.commandGateway().<ItemRenamedV2>send(new RenameItemV2("i-1", "marker"))
                .get(30, TimeUnit.SECONDS);
        assertThat(fromEvents.getReason()).isEqualTo("was pencil");

        ItemRenamedV2 fromSnapshot = caller.commandGateway().<ItemRenamedV2>send(new RenameItemV2("i-2", "mug"))
                .get(30, TimeUnit.SECONDS);
        assertThat(fromSnapshot.getReason()).isEqualTo("was cup");
    }

    @Test
    void anEventWrittenNowRecordsItsRevisionAndIsReadAsWritten() throws Exception {
        caller.commandGateway().send(new AddItem("i-3", "book", 4)).get(30, TimeUnit.SECONDS);

        var stored = broker.eventStore().allEvents().stream()
                .filter(e -> "i-3".equals(e.getAggregateId())).findFirst().orElseThrow();
        assertThat(stored.getEventMessage().getSerializedPayload().getRevision()).isEqualTo(1);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            var item = ItemStore.items.get("i-3");
            assertThat(item).isNotNull();
            assertThat(item.getTitle()).isEqualTo("book");
            assertThat(item.getQuantity()).isEqualTo(4);
        });
    }

    @Test
    void callersStillOnTheOldNamesReachTheRenamedHandlers() throws Exception {
        ItemRenamedV2 renamed = caller.commandGateway().<ItemRenamedV2>send(new RenameItem("i-1", "crayon"))
                .get(30, TimeUnit.SECONDS);
        assertThat(renamed.getTitle()).isEqualTo("crayon");
        assertThat(renamed.getReason()).isEqualTo("was pencil");

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Single<ItemView> found = caller.queryGateway().query(new FindItem("i-1")).get(30, TimeUnit.SECONDS);
            assertThat(found.getData().getTitle()).isEqualTo("crayon");
        });
    }

    /** An event as the previous version wrote it: revision 0, maybe another class name, another shape. */
    private static DomainEventMessage legacyEvent(com.evento.common.modeling.messaging.payload.DomainEvent current,
                                                  String formerSimpleName, Consumer<ObjectNode> toOldShape)
            throws Exception {
        var message = new DomainEventMessage(current);
        legacy(message.getSerializedPayload(), formerSimpleName, toOldShape);
        return message;
    }

    private static <S extends SerializedObject<?>> S legacy(S payload, String formerSimpleName,
                                                             Consumer<ObjectNode> toOldShape) throws Exception {
        var typed = (ArrayNode) JSON.readTree(payload.getSerializedObject());
        toOldShape.accept((ObjectNode) typed.get(1));
        if (formerSimpleName != null) {
            var className = typed.get(0).asText();
            var formerClassName = className.substring(0, className.lastIndexOf('.') + 1) + formerSimpleName;
            typed.set(0, JSON.getNodeFactory().textNode(formerClassName));
            payload.setObjectClass("class " + formerClassName);
        }
        payload.setSerializedObject(JSON.writeValueAsString(typed));
        payload.setRevision(0);
        return payload;
    }
}
