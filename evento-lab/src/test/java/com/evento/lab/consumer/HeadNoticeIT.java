package com.evento.lab.consumer;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.support.EmbeddedBroker;
import com.evento.lab.support.TestEventStoreBundleClient;
import com.evento.transport.HandshakeProtocol;
import com.evento.transport.protocol.ProtocolNotifications;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Head notices ({@link HandshakeProtocol#CAPABILITY_HEAD}): a consumer at the head waits for the
 * broker to say its store has grown, up to its fetch delay, instead of fetching again every few
 * milliseconds. A manufacturing-execution system, ~100 consumers: the empty fetches were ~730 event-store queries a second at
 * rest, and the fetches that had events queued behind them on the broker.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class HeadNoticeIT {

    /** Long enough that only a notice explains a prompt delivery. */
    private static final int FETCH_DELAY_MS = 20_000;

    private EmbeddedBroker broker;
    private TestEventStoreBundleClient eventStore;
    private EventoBundle bundle;

    @AfterEach
    void tearDown() throws Exception {
        if (bundle != null) {
            bundle.getEngineSupervisor().stop(Duration.ofSeconds(10));
            bundle = null;
        }
        if (eventStore != null) { eventStore.close(); eventStore = null; }
        if (broker != null) { broker.close(); broker = null; }
    }

    private void start(String bundleId, Set<String> brokerCapabilities) throws Exception {
        LabStore.reset();
        broker = new EmbeddedBroker("lab-broker", 0, brokerCapabilities);
        eventStore = new TestEventStoreBundleClient(broker.port());
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(com.evento.lab.bundle.consumer.LabProjector.class.getPackage())
                .setBundleId(bundleId)
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .setSssFetchDelay(FETCH_DELAY_MS)
                .start();
        await().atMost(15, TimeUnit.SECONDS).until(() -> broker.lifecycle().isBundleAvailable(bundleId));
    }

    private void announce(long head) {
        broker.lifecycle().broadcast(HandshakeProtocol.CAPABILITY_HEAD, ProtocolNotifications.EVENTS_APPENDED,
                ByteBuffer.allocate(Long.BYTES).putLong(head).array());
    }

    @Test
    void aConsumerAtTheHeadWaitsForTheNoticeAndFetchesAtOnce() throws Exception {
        start("lab-head-notice", Set.of(HandshakeProtocol.CAPABILITY_HEAD));
        Thread.sleep(500);  // every engine past its first, empty fetch: waiting at the head

        long seq = eventStore.publish(new OrderCreatedEvent("head-1", "d1", 1), "head-1");
        Thread.sleep(1_000);
        assertThat(LabStore.get("head-1"))
                .as("without a notice the consumer keeps waiting (its fetch delay is %d ms)", FETCH_DELAY_MS)
                .isNull();

        announce(seq);
        await().atMost(3, TimeUnit.SECONDS).until(() -> LabStore.get("head-1") != null);
    }

    @Test
    void withoutTheCapabilityTheConsumersPollAsBefore() throws Exception {
        start("lab-head-polling", Set.of());
        Thread.sleep(500);

        eventStore.publish(new OrderCreatedEvent("poll-1", "d1", 1), "poll-1");
        await().atMost(3, TimeUnit.SECONDS)
                .alias("no notice, no long wait: a broker without the capability is polled")
                .until(() -> LabStore.get("poll-1") != null);
    }
}
