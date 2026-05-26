package com.evento.lab.consumer;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.lab.api.event.OrderCreatedEvent;
import com.evento.lab.bundle.LabBundleInterceptor;
import com.evento.lab.bundle.LabStore;
import com.evento.lab.bundle.consumer.LabProjector;
import com.evento.lab.support.EmbeddedBroker;
import com.evento.lab.support.TestEventStoreBundleClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies projector-level interceptor failures (BEFORE and AFTER handling)
 * using the {@link LabBundleInterceptor} metadata flags.
 *
 * <h2>Retry-forever behaviour</h2>
 * <p>{@code @EventHandler} without an explicit {@code retry} value defaults to
 * {@code retry = -1}, which means the framework retries indefinitely with a
 * 1 s delay between attempts.  An interceptor failure therefore keeps the
 * projector busy-retrying on the same event — the projector never advances to
 * later events.
 *
 * <p>Tests here verify:
 * <ul>
 *   <li><b>BEFORE fail</b> — handler is never invoked ({@code LabStore.get()}
 *       stays {@code null}) and the bundle stays alive despite the retry loop.</li>
 *   <li><b>AFTER fail</b> — handler IS invoked (side effect in
 *       {@code LabStore} is visible), then the after-interceptor throws and the
 *       event is re-delivered, but the bundle keeps running.</li>
 * </ul>
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ConsumerInterceptorIT {

    private EmbeddedBroker broker;
    private TestEventStoreBundleClient eventStore;
    private EventoBundle bundle;

    @BeforeEach
    void setUp() throws Exception {
        LabStore.reset();
        broker = new EmbeddedBroker();
        eventStore = new TestEventStoreBundleClient(broker.port());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (bundle != null) {
            bundle.getEngineSupervisor().stop(Duration.ofSeconds(10));
            bundle = null;
        }
        if (eventStore != null) { eventStore.close(); eventStore = null; }
        if (broker != null) { broker.close(); broker = null; }
    }

    private void startBundleWithInterceptor() throws Exception {
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(LabProjector.class.getPackage())
                .setBundleId("lab-ci-bundle")
                .setEventoServerMessageBusConfiguration(
                        new EventoServerMessageBusConfiguration(
                                new ClusterNodeAddress("127.0.0.1", broker.port())))
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .setMessageHandlerInterceptor(new LabBundleInterceptor())
                .start();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> broker.lifecycle().isBundleAvailable("lab-ci-bundle"));
    }

    @Test
    void projectorBeforeInterceptorFiresAndHandlerIsNotInvoked() throws Exception {
        startBundleWithInterceptor();

        String failId = "ci-before-" + UUID.randomUUID();

        // The before-interceptor throws for this event; the handler must never run.
        // With retry=-1 the projector retries indefinitely — allow 3 s of retries
        // (≥ 2 cycles at 1 s retryDelay) to confirm the handler is still skipped.
        eventStore.publishWithMetadata(
                new OrderCreatedEvent(failId, "will-fail", 1),
                failId,
                Map.of("failBeforeProjector", "true"));

        // Wait long enough for at least 2 retry cycles to have elapsed, then verify
        // the bundle is still alive and the handler never wrote to LabStore.
        await().pollDelay(3, TimeUnit.SECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(bundle.getEngineSupervisor().isShuttingDown()).isFalse());

        assertThat(LabStore.get(failId)).isNull();
    }

    @Test
    void projectorAfterInterceptorHandlerRunsButBundleStaysAlive() throws Exception {
        startBundleWithInterceptor();

        String failId = "ci-after-" + UUID.randomUUID();

        // The after-interceptor throws; the handler runs first and writes to LabStore,
        // then the event is retried forever.  Verify the handler side-effect IS visible
        // and the bundle stays alive.
        eventStore.publishWithMetadata(
                new OrderCreatedEvent(failId, "after-fail", 1),
                failId,
                Map.of("failAfterProjector", "true"));

        // LabProjector's handler ran and called LabStore.put() before after-interceptor threw.
        await().atMost(15, TimeUnit.SECONDS)
                .until(() -> LabStore.get(failId) != null);

        assertThat(LabStore.get(failId).getDescription()).isEqualTo("after-fail");
        assertThat(bundle.getEngineSupervisor().isShuttingDown()).isFalse();
    }
}
