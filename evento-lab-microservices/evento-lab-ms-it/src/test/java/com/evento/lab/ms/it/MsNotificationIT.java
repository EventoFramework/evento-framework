package com.evento.lab.ms.it;

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
 * Integration tests for the notification service and observer notification flow.
 * No Docker required.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class MsNotificationIT {

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

    @Test
    void observerSendsNotificationOnOrderCreated() throws Exception {
        harness.withObserverBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-observer"));

        harness.eventStore().publish(new OrderCreatedEvent("notif-1", "Notification Order", 1), "notif-1");

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> MsObservedEvents.getAll().contains("created:notif-1"));

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> MsNotificationLog.getAll().stream()
                        .anyMatch(entry -> entry.startsWith("EMAIL:notif-1:")));

        assertThat(MsObservedEvents.getAll()).contains("created:notif-1");
        assertThat(MsNotificationLog.getAll())
                .anyMatch(entry -> entry.equals("EMAIL:notif-1:Order created: notif-1"));
    }

    @Test
    void notificationServiceHandlesSendNotificationCommand() throws Exception {
        harness.withObserverBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-observer"));

        // Publish multiple order events and verify notifications
        harness.eventStore().publish(new OrderCreatedEvent("notif-multi-1", "Multi Order 1", 1), "notif-multi-1");
        harness.eventStore().publish(new OrderCreatedEvent("notif-multi-2", "Multi Order 2", 1), "notif-multi-2");

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> MsObservedEvents.getAll().contains("created:notif-multi-1")
                        && MsObservedEvents.getAll().contains("created:notif-multi-2"));

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> MsNotificationLog.getAll().stream()
                                .filter(e -> e.startsWith("EMAIL:notif-multi-"))
                                .count() >= 2);

        assertThat(MsNotificationLog.getAll())
                .anyMatch(entry -> entry.equals("EMAIL:notif-multi-1:Order created: notif-multi-1"))
                .anyMatch(entry -> entry.equals("EMAIL:notif-multi-2:Order created: notif-multi-2"));
    }

    @Test
    void confirmationEventTriggersEmailNotification() throws Exception {
        harness.withObserverBundle();
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> harness.broker().lifecycle().isBundleAvailable("lab-ms-observer"));

        harness.eventStore().publishServiceEvent(new OrderConfirmedEvent("notif-confirm-1"));

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> MsObservedEvents.getAll().contains("confirmed:notif-confirm-1"));

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> MsNotificationLog.getAll().stream()
                        .anyMatch(entry -> entry.equals("EMAIL:notif-confirm-1:Order confirmed: notif-confirm-1")));

        assertThat(MsNotificationLog.getAll())
                .anyMatch(entry -> entry.equals("EMAIL:notif-confirm-1:Order confirmed: notif-confirm-1"));
    }
}
