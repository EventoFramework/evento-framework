package com.evento.lab.ms.it;

import com.evento.common.modeling.messaging.message.application.ServiceEventMessage;
import com.evento.lab.ms.api.event.OrderCreatedEvent;
import com.evento.lab.ms.api.event.OrderConfirmedEvent;
import com.evento.lab.ms.it.support.MsHarness;
import com.evento.lab.ms.observer.observer.MsObservedEvents;
import com.evento.lab.ms.observer.store.MsNotificationLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Regression tests for the {@code ServiceEventMessage} null-objectClass NPE fix
 * (see {@code Message.getPayloadName()}).
 *
 * <p>Background: the Iris platform {@code testImpersonate} test exercised a PUT
 * endpoint ({@code /auth/security-scope/{identifier}}) on an Evento-based server.
 * That endpoint internally processed commands and events, then Spring tried to serialize
 * the REST response body — which included a {@link ServiceEventMessage} whose
 * {@code serializedPayload.objectClass} field was {@code null} (event stored in the DB
 * without a payload class name, e.g. a null-payload event or old data).
 *
 * <p>The fix: {@code Message.getPayloadName()} now guards {@code getType()} for null
 * and returns {@code null} instead of throwing an NPE. These tests verify:
 * <ol>
 *   <li>Direct assertion — {@code getEventName()} returns {@code null} on a corrupted message
 *       rather than throwing.
 *   <li>Integration — the consumer pipeline processes a corrupted event without crashing and
 *       continues to deliver subsequent events correctly.
 * </ol>
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class MsNullPayloadRegressionIT {

    private MsHarness harness;

    @BeforeEach
    void setUp() throws Exception {
        MsObservedEvents.reset();
        MsNotificationLog.reset();
        harness = new MsHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) harness.close();
    }

    /**
     * Directly verifies the regression fix: a {@link ServiceEventMessage} constructed with a
     * null payload (and thus {@code objectClass = null}) must return {@code null} from
     * {@code getEventName()} / {@code getPayloadName()} rather than throwing NPE.
     *
     * <p>This is the message-level analog of the Iris server 500 error — the serialization
     * path called {@code getEventName()} on such a message and crashed.
     */
    @Test
    void nullObjectClass_getEventName_returnsNullWithoutNpe() {
        // ServiceEventMessage(null) → SerializedPayload(null) → objectClass = null
        var msg = new ServiceEventMessage(null);

        // Before the fix these threw NPE; after the fix they return null.
        assertThat(msg.getEventName()).isNull();
        assertThat(msg.getPayloadName()).isNull();
        assertThat(msg.getType()).isNull();
    }

    /**
     * Integration regression: publish a corrupted event (null objectClass) followed by a
     * normal event. The consumer pipeline must not crash; the normal event must be delivered.
     *
     * <p>Emulates the Iris {@code testImpersonate} scenario where a PUT request triggers
     * internal event processing that includes a corrupted {@link ServiceEventMessage}.
     * The observer bundle plays the role of the Iris service handler that reads those events.
     */
    @Test
    void corruptedEventInStore_pipelineContinuesDelivery() throws Exception {
        harness.withObserverBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-observer"));

        // Corrupted event: null objectClass, unknown event name → no handler matches, skipped gracefully.
        harness.eventStore().publishCorrupted("UnknownServiceEvent", "agg-corrupted-1");

        // Normal event after the corrupted one must still reach its handler.
        harness.eventStore().publish(
                new OrderCreatedEvent("null-reg-1", "Post-corruption order", 1), "null-reg-1");

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> MsObservedEvents.getAll().contains("created:null-reg-1"));

        // Bundle is still alive and the normal event was processed.
        assertThat(harness.broker().lifecycle().isBundleAvailable("lab-ms-observer")).isTrue();
        assertThat(MsObservedEvents.getAll()).contains("created:null-reg-1");
    }

    /**
     * Integration: mix of corrupted events, domain events, and service events — all consumer
     * types (observer + notification service) must keep working across the corruption boundary.
     *
     * <p>Models the Iris flow more faithfully: a sequence like
     * "save security scope → create user → assign role → enable impersonation" generates a
     * mix of service events, some of which may carry null objectClass from legacy DB rows.
     */
    @Test
    void mixedCorruptedAndNormalEvents_allConsumersRemainOperational() throws Exception {
        harness.withObserverBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-observer"));

        // Sequence: corrupted → normal domain → corrupted → normal service
        harness.eventStore().publishCorrupted("LegacyScopeCreatedEvent", "scope-1");
        harness.eventStore().publish(
                new OrderCreatedEvent("mix-order-1", "Mixed test order", 2), "mix-order-1");
        harness.eventStore().publishCorrupted("LegacyUserCreatedEvent", "user-1");
        harness.eventStore().publishServiceEvent(new OrderConfirmedEvent("mix-order-1"));

        // Both normal events must arrive at the observer.
        await().atMost(25, TimeUnit.SECONDS)
                .until(() -> MsObservedEvents.getAll().contains("created:mix-order-1")
                        && MsObservedEvents.getAll().contains("confirmed:mix-order-1"));

        assertThat(harness.broker().lifecycle().isBundleAvailable("lab-ms-observer")).isTrue();
        assertThat(MsObservedEvents.getAll())
                .contains("created:mix-order-1", "confirmed:mix-order-1");

        // Notification log should have entries for both normal events.
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> MsNotificationLog.getAll().stream()
                        .filter(e -> e.contains("mix-order-1"))
                        .count() >= 2);
        assertThat(MsNotificationLog.getAll())
                .anyMatch(e -> e.equals("EMAIL:mix-order-1:Order created: mix-order-1"))
                .anyMatch(e -> e.equals("EMAIL:mix-order-1:Order confirmed: mix-order-1"));
    }
}