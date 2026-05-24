package com.evento.server.bus.event;

import com.evento.server.bus.NodeAddress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BusEventBusTest {

    private final BusEventBus bus = new BusEventBus();

    private NodeAddress addr(String inst) { return new NodeAddress("b", 1L, inst); }

    @Test
    void deliversToAllSubscribers() {
        var seenA = new ArrayList<BusEvent>();
        var seenB = new ArrayList<BusEvent>();
        bus.subscribe(seenA::add);
        bus.subscribe(seenB::add);

        var event = new BusEvent.NodeJoined(addr("i1"), Instant.now());
        bus.publish(event);

        assertThat(seenA).containsExactly(event);
        assertThat(seenB).containsExactly(event);
    }

    @Test
    void unsubscribeStopsDelivery() {
        var seen = new ArrayList<BusEvent>();
        var sub = (java.util.function.Consumer<BusEvent>) seen::add;
        bus.subscribe(sub);
        bus.publish(new BusEvent.NodeJoined(addr("i1"), Instant.now()));
        bus.unsubscribe(sub);
        bus.publish(new BusEvent.NodeLeft(addr("i1"), "test", Instant.now()));

        assertThat(seen).hasSize(1);
    }

    @Test
    void subscriberExceptionDoesNotBlockOthers() {
        var seen = new ArrayList<BusEvent>();
        bus.subscribe(e -> { throw new RuntimeException("boom"); });
        bus.subscribe(seen::add);
        bus.publish(new BusEvent.NodeJoined(addr("i1"), Instant.now()));

        assertThat(seen).hasSize(1);
    }

    @Test
    void publishWithNoSubscribersIsNoOp() {
        bus.publish(new BusEvent.NodeLeft(addr("x"), null, Instant.now()));
        assertThat(bus.subscriberCount()).isZero();
    }

    @Test
    void concurrentSubscriptionDoesNotBlockPublish() throws Exception {
        int threads = 16;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        bus.subscribe(e -> {});
                        bus.publish(new BusEvent.NodeJoined(addr("i" + idx), Instant.now()));
                    } catch (Throwable ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        // Each thread added one subscriber.
        assertThat(bus.subscriberCount()).isEqualTo(threads);
    }

    @Test
    void sealedPatternMatchExhaustive() {
        // Demonstrates the OCP-friendly handoff: subscribers handle the cases
        // they care about and ignore the rest with a default branch.
        List<Object> handled = new ArrayList<>();
        bus.subscribe(e -> {
            switch (e) {
                case BusEvent.NodeJoined j -> handled.add("join:" + j.node().instanceId());
                case BusEvent.BundleRegistered ignored -> {}
                case BusEvent.BundleDiscovered ignored -> {}
                case BusEvent.NodeLeft l -> handled.add("leave:" + l.node().instanceId());
                case BusEvent.NodeEnabled ignored -> {}
                case BusEvent.NodeDisabled ignored -> {}
                case BusEvent.HeartbeatTimeout ignored -> {}
                case BusEvent.ViewChanged ignored -> {}
                case BusEvent.AvailableViewChanged ignored -> {}
                case BusEvent.AdminNotification ignored -> {}
            }
        });
        bus.publish(new BusEvent.NodeJoined(addr("i1"), Instant.now()));
        bus.publish(new BusEvent.NodeLeft(addr("i2"), null, Instant.now()));
        bus.publish(new BusEvent.NodeEnabled(addr("i3"), Instant.now()));

        assertThat(handled).containsExactly("join:i1", "leave:i2");
    }
}
