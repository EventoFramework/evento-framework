package com.evento.common.versioning;

import com.evento.common.modeling.messaging.dto.PublishedEvent;
import com.evento.common.modeling.messaging.message.application.DomainEventMessage;
import com.evento.common.modeling.messaging.message.application.SerializedPayload;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.evento.common.serialization.ObjectMapperUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpcastersTest {

    /** Revision 0 had "name" and no quantity; revision 1 calls it "title" and has a quantity. */
    @Revision(1)
    public static class ItemAdded extends DomainEvent {
        private String itemId;
        private String title;
        private int quantity;
        private List<String> tags = new ArrayList<>();
        private BigDecimal price;
        private long serial;

        public ItemAdded() {
        }

        public ItemAdded(String itemId, String title, int quantity) {
            this.itemId = itemId;
            this.title = title;
            this.quantity = quantity;
        }
    }

    /** Replaces an "ItemRenamed" class that no longer exists: the "new class called V2" way. */
    public static class ItemRenamedV2 extends DomainEvent {
        private String itemId;
        private String title;
        private String reason;
    }

    @Revision(2)
    public static class Priced extends DomainEvent {
        private String amount;
    }

    private static final String ITEM_ADDED = ItemAdded.class.getName();
    private static final String ITEM_ADDED_NAME = Revisions.payloadName(ItemAdded.class);

    /** A payload as an older bundle stored it. */
    private static SerializedPayload<DomainEvent> stored(String className, String fields, int revision) {
        var p = new SerializedPayload<DomainEvent>();
        p.setSerializedObject("[\"" + className + "\"," + fields + "]");
        p.setObjectClass("class " + className);
        p.setRevision(revision);
        return p;
    }

    private static final Upcaster NAME_TO_TITLE = Upcaster.from(ItemAdded.class, 0).to(1, json -> {
        json.set("title", json.remove("name"));
        json.put("quantity", 1);
    });

    @Test
    void aPayloadRecordsTheRevisionOfItsClassAndOmitsRevisionZero() throws Exception {
        var revised = new SerializedPayload<DomainEvent>(new ItemAdded("i-1", "pen", 2));
        assertThat(revised.getRevision()).isEqualTo(1);
        assertThat(ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(revised)).contains("\"revision\":1");

        var unrevised = new SerializedPayload<DomainEvent>(new ItemRenamedV2());
        assertThat(unrevised.getRevision()).isZero();
        assertThat(ObjectMapperUtils.getPayloadObjectMapper().writeValueAsString(unrevised)).doesNotContain("revision");
    }

    @Test
    void aPayloadStoredBeforeRevisionsReadsAsRevisionZero() throws Exception {
        // The envelope as a 2.5 server stored it: no "revision" property.
        var mapper = ObjectMapperUtils.getPayloadObjectMapper();
        var json = mapper.writeValueAsString(stored(ITEM_ADDED, "{\"itemId\":\"i-1\",\"name\":\"pen\"}", 0));
        assertThat(json).doesNotContain("revision");
        var read = (SerializedPayload<?>) mapper.readValue(json, Object.class);
        assertThat(read.getRevision()).isZero();
    }

    @Test
    void aRevisionStepReshapesTheStoredJsonBeforeItIsRead() {
        var payload = stored(ITEM_ADDED, "{\"itemId\":\"i-1\",\"name\":\"pen\",\"context\":\"default\"}", 0);

        assertThat(Upcasters.of(NAME_TO_TITLE).upcast(payload)).isTrue();

        var item = (ItemAdded) payload.getObject();
        assertThat(item.itemId).isEqualTo("i-1");
        assertThat(item.title).isEqualTo("pen");
        assertThat(item.quantity).isEqualTo(1);
        assertThat(payload.getRevision()).isEqualTo(1);
        assertThat(payload.getObjectClass()).isEqualTo("class " + ITEM_ADDED);
    }

    @Test
    void stepsChainInOrderUntilNoneStartsWhereTheLastEnded() {
        var calls = new ArrayList<String>();
        var chain = Upcasters.of(
                Upcaster.from(Priced.class, 1).to(2, json -> {
                    calls.add("1->2");
                    json.put("amount", json.remove("cents").asLong() / 100 + " EUR");
                }),
                Upcaster.from(Priced.class, 0).to(1, json -> {
                    calls.add("0->1");
                    json.set("cents", json.remove("value"));
                }));
        var payload = stored(Priced.class.getName(), "{\"value\":1250}", 0);

        chain.upcast(payload);

        assertThat(calls).containsExactly("0->1", "1->2");
        assertThat(((Priced) payload.getObject()).amount).isEqualTo("12 EUR");
        assertThat(payload.getRevision()).isEqualTo(2);
    }

    @Test
    void aRenameTurnsAPayloadOfADeletedClassIntoTheNewClassAndRoutesItByItsNewName() {
        var chain = Upcasters.of(Upcaster.from("ItemRenamed", 0).to(ItemRenamedV2.class, 0,
                json -> json.put("reason", "unknown")));
        var message = new DomainEventMessage();
        message.setSerializedPayload(stored("com.old.ItemRenamed", "{\"itemId\":\"i-1\",\"title\":\"pencil\"}", 0));
        var event = new PublishedEvent();
        event.setEventMessage(message);
        event.setEventName("ItemRenamed");

        assertThat(chain.upcast(event)).isTrue();

        assertThat(event.getEventName()).isEqualTo(Revisions.payloadName(ItemRenamedV2.class));
        assertThat(message.getEventName()).isEqualTo(Revisions.payloadName(ItemRenamedV2.class));
        var renamed = (ItemRenamedV2) message.getPayload();
        assertThat(renamed.title).isEqualTo("pencil");
        assertThat(renamed.reason).isEqualTo("unknown");
    }

    @Test
    void aRenamedPayloadContinuesThroughTheStepsOfItsNewClass() {
        var chain = Upcasters.of(
                Upcaster.from("OldItem", 3).to(ItemAdded.class, 0),
                NAME_TO_TITLE);
        var payload = stored("com.old.OldItem", "{\"itemId\":\"i-9\",\"name\":\"cup\"}", 3);

        chain.upcast(payload);

        var item = (ItemAdded) payload.getObject();
        assertThat(item.title).isEqualTo("cup");
        assertThat(payload.getRevision()).isEqualTo(1);
    }

    @Test
    void aCurrentPayloadIsLeftExactlyAsStored() {
        var payload = new SerializedPayload<DomainEvent>(new ItemAdded("i-1", "pen", 2));
        var before = payload.getSerializedObject();

        assertThat(Upcasters.of(NAME_TO_TITLE).upcast(payload)).isFalse();
        assertThat(payload.getSerializedObject()).isSameAs(before);
    }

    @Test
    void aPayloadFromANewerBundleIsReadAsItIs() {
        // Revision 5 is ahead of this class: no step starts there, Jackson reads what it knows.
        var payload = stored(ITEM_ADDED, "{\"itemId\":\"i-1\",\"title\":\"pen\",\"colour\":\"red\"}", 5);

        assertThat(Upcasters.of(NAME_TO_TITLE).upcast(payload)).isFalse();
        assertThat(((ItemAdded) payload.getObject()).title).isEqualTo("pen");
    }

    @Test
    void fieldsAStepDoesNotTouchSurviveExactly() {
        var payload = stored(ITEM_ADDED, "{\"itemId\":\"i-1\",\"name\":\"pen\","
                + "\"tags\":[\"java.util.ArrayList\",[\"a\",\"b\"]],"
                + "\"price\":[\"java.math.BigDecimal\",1.10],\"serial\":9007199254740993}", 0);

        Upcasters.of(NAME_TO_TITLE).upcast(payload);

        var item = (ItemAdded) payload.getObject();
        assertThat(item.tags).containsExactly("a", "b");
        assertThat(item.price).isEqualByComparingTo("1.10");
        assertThat(item.price.scale()).isEqualTo(2);
        assertThat(item.serial).isEqualTo(9007199254740993L);
    }

    @Test
    void aFailingStepNamesThePayloadAndTheRevisionItStartedFrom() {
        var chain = Upcasters.of(Upcaster.from(ItemAdded.class, 0).to(1, json -> {
            throw new IllegalStateException("boom");
        }));

        assertThatThrownBy(() -> chain.upcast(stored(ITEM_ADDED, "{\"itemId\":\"i-1\"}", 0)))
                .isInstanceOf(UpcastException.class)
                .hasMessageContaining(ITEM_ADDED_NAME)
                .hasMessageContaining("revision 0")
                .hasRootCauseMessage("boom");
    }

    @Test
    void aPayloadThatIsNotTypedJsonIsRefusedWithItsName() {
        var payload = stored(ITEM_ADDED, "{}", 0);
        payload.setSerializedObject("{\"not\":\"typed\"}");

        assertThatThrownBy(() -> Upcasters.of(NAME_TO_TITLE).upcast(payload))
                .isInstanceOf(UpcastException.class)
                .hasMessageContaining("not a typed JSON object");
    }

    @Test
    void anInvalidChainIsRefusedWhenBuilt() {
        assertThatThrownBy(() -> Upcasters.of(NAME_TO_TITLE, Upcaster.from(ItemAdded.class, 0).to(2, json -> { })))
                .hasMessageContaining("two upcasters start from");
        assertThatThrownBy(() -> Upcasters.of(
                Upcaster.from("A", 0).to(ItemRenamedV2.class, 0),
                Upcaster.from(ItemRenamedV2.class, 0).to(Priced.class, 0),
                Upcaster.from(Priced.class, 0).to(ItemAdded.class, 0),
                Upcaster.from(ItemAdded.class, 0).to(ItemRenamedV2.class, 0)))
                .hasMessageContaining("loop");
        assertThatThrownBy(() -> Upcaster.from(ItemAdded.class, 1).to(1, json -> { }))
                .hasMessageContaining("must raise its revision");
        assertThatThrownBy(() -> Upcaster.from("com.app.ItemAdded", 0))
                .hasMessageContaining("simple class name");
        assertThatThrownBy(() -> Upcaster.from("ItemAdded", -1))
                .hasMessageContaining("0 or more");
    }

    @Test
    void formerNamesOfHandledPayloadsAreTheirAliases() {
        var v2 = Revisions.payloadName(ItemRenamedV2.class);
        var chain = Upcasters.of(
                Upcaster.from("ItemRenamed", 0).to(ItemRenamedV2.class, 0),
                Upcaster.from("ItemRenamedV0", 0).to(ItemRenamedV2.class, 0, json -> { }),
                Upcaster.from("SomethingElse", 0).to(Priced.class, 2),
                NAME_TO_TITLE);

        assertThat(chain.aliasesOf(Set.of(v2, ITEM_ADDED_NAME)))
                .containsExactly(java.util.Map.entry("ItemRenamed", v2), java.util.Map.entry("ItemRenamedV0", v2));
        assertThat(chain.aliasesOf(Set.of())).isEmpty();
    }

    @Test
    void payloadNamesMatchHowEventoRoutes() {
        assertThat(Revisions.payloadName("class com.app.OrderPlaced")).isEqualTo("OrderPlaced");
        assertThat(Revisions.payloadName("com.app.OrderPlaced")).isEqualTo("OrderPlaced");
        assertThat(Revisions.payloadName("class OrderPlaced")).isEqualTo("OrderPlaced");
        assertThat(Revisions.of(ItemAdded.class)).isEqualTo(1);
        assertThat(Revisions.of(ItemRenamedV2.class)).isZero();
    }
}
