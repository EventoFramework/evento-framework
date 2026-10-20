package com.evento.test.broker;

import com.evento.application.EventoBundle;
import com.evento.application.consumer.ConsumerEngineConfig;
import com.evento.common.modeling.messaging.query.Single;
import com.evento.test.broker.demo.CountOrders;
import com.evento.test.broker.demo.CreateOrder;
import com.evento.test.broker.demo.NoteAdded;
import com.evento.test.broker.demo.OpenOrder;
import com.evento.test.broker.demo.OrderAggregate;
import com.evento.test.broker.demo.OrderCount;
import com.evento.test.broker.demo.OrderCreated;
import com.evento.test.broker.demo.Orders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A2: a bundle under test against the in-process broker: its aggregate command is stored as an
 * event by the server's own command handling, its projector reads it, its projection answers a
 * query, and an event published by the test reaches the projector too.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class EventoTestBrokerTest {

    private EventoTestBroker broker;
    private EventoBundle bundle;

    @BeforeEach
    void start() throws Exception {
        Orders.SEEN.clear();
        broker = EventoTestBroker.start();
        bundle = EventoBundle.Builder.builder()
                .setBasePackage(OrderAggregate.class.getPackage())
                .setBundleId("orders")
                .setEventoServerMessageBusConfiguration(broker.busConfiguration())
                .setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)
                .start();
    }

    @AfterEach
    void stop() {
        if (bundle != null) bundle.stop(Duration.ofSeconds(10));
        if (broker != null) broker.close();
    }

    @Test
    void aCommandIsStoredReadByTheProjectorAndQueried() throws Exception {
        bundle.getCommandGateway().send(new CreateOrder("o-1")).get(10, TimeUnit.SECONDS);

        assertThat(broker.events()).hasSize(1);
        assertThat(broker.events().getFirst().getAggregateId()).isEqualTo("o-1");
        assertThat(broker.events().getFirst().getEventSequenceNumber()).isEqualTo(1);
        await().atMost(10, TimeUnit.SECONDS).until(() -> Orders.SEEN.contains("o-1"));

        Single<OrderCount> answer = bundle.getQueryGateway().query(new CountOrders()).get(10, TimeUnit.SECONDS);
        assertThat(answer.getData().count()).isEqualTo(1);
    }

    @Test
    void anEventPublishedByTheTestReachesTheConsumers() {
        broker.publish(new OrderCreated("o-from-test"), "o-from-test");
        await().atMost(10, TimeUnit.SECONDS).until(() -> Orders.SEEN.contains("o-from-test"));
    }

    @Test
    void aSecondCommandOnTheAggregateSeesItsStory() throws Exception {
        bundle.getCommandGateway().send(new CreateOrder("o-2")).get(10, TimeUnit.SECONDS);
        // CreateOrder is the aggregate's init command: a second one on the same id is refused,
        // which only happens if the broker sent the story it stored.
        var second = bundle.getCommandGateway().send(new CreateOrder("o-2"));
        assertThat(second).failsWithin(10, TimeUnit.SECONDS);
        assertThat(broker.events()).hasSize(1);
    }

    @Test
    void theEventsOfOneCommandAreStoredTogetherAndTheCallerGetsTheLast() throws Exception {
        Object answer = bundle.getCommandGateway().send(new OpenOrder("o-3", "fragile")).get(10, TimeUnit.SECONDS);

        assertThat(answer).isInstanceOf(NoteAdded.class);
        assertThat(broker.events()).extracting(e -> e.getEventName()).containsExactly("OrderCreated", "NoteAdded");
        assertThat(broker.events()).extracting(e -> e.getEventSequenceNumber()).containsExactly(1L, 2L);
        await().atMost(10, TimeUnit.SECONDS).until(() -> Orders.SEEN.containsAll(java.util.List.of("o-3", "o-3:fragile")));

        // The aggregate exists: both events are its story.
        assertThat(bundle.getCommandGateway().send(new CreateOrder("o-3"))).failsWithin(10, TimeUnit.SECONDS);
    }
}
