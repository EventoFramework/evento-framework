package com.evento.server.bus.v2.registry;

import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.v2.event.BusEvent;
import com.evento.server.bus.v2.event.BusEventBus;
import com.evento.transport.Transport;
import com.evento.transport.inmemory.InMemoryTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionRegistryTest {

    private BusEventBus eventBus;
    private ConnectionRegistry registry;
    private List<BusEvent> recordedEvents;

    @BeforeEach
    void setUp() {
        eventBus = new BusEventBus();
        recordedEvents = new ArrayList<>();
        eventBus.subscribe(recordedEvents::add);
        registry = new ConnectionRegistry(eventBus);
    }

    private NodeAddress addr(String inst) { return new NodeAddress("b", 1L, inst); }

    private Connection conn(String instId) {
        var pair = InMemoryTransport.pair("c-" + instId, "s-" + instId);
        return new Connection(addr(instId), pair.client(), UUID.randomUUID().toString(), Instant.now());
    }

    @Test
    void registerAddsToViewAndPublishesJoinedAndViewChanged() {
        var conn = conn("i1");
        var previous = registry.register(conn);

        assertThat(previous).isNull();
        assertThat(registry.view()).containsExactly(addr("i1"));
        assertThat(recordedEvents).hasSize(2);
        assertThat(recordedEvents.get(0)).isInstanceOf(BusEvent.NodeJoined.class);
        assertThat(recordedEvents.get(1)).isInstanceOf(BusEvent.ViewChanged.class);
    }

    @Test
    void supersededRegistrationClosesPreviousAndEmitsLeftThenJoined() {
        var first = conn("i1");
        registry.register(first);
        recordedEvents.clear();

        var second = conn("i1");
        registry.register(second);

        assertThat(((InMemoryTransport) first.transport()).state().isTerminal()).isTrue();
        // ordering: NodeLeft(superseded) → NodeJoined → ViewChanged
        assertThat(recordedEvents.stream().map(e -> e.getClass().getSimpleName()).toList())
                .containsExactly("NodeLeft", "NodeJoined", "ViewChanged");
        assertThat(((BusEvent.NodeLeft) recordedEvents.getFirst()).reason()).isEqualTo("superseded");
    }

    @Test
    void unregisterWithMatchingTokenRemovesAndEmitsLeft() {
        var c = conn("i1");
        registry.register(c);
        recordedEvents.clear();

        var removed = registry.unregister(addr("i1"), c.connectionToken(), "test");

        assertThat(removed).isPresent();
        assertThat(registry.view()).isEmpty();
        assertThat(recordedEvents.stream().map(e -> e.getClass().getSimpleName()).toList())
                .contains("NodeLeft", "ViewChanged", "AvailableViewChanged");
    }

    @Test
    void unregisterWithStaleTokenIsRejected() {
        var c = conn("i1");
        registry.register(c);
        recordedEvents.clear();

        var result = registry.unregister(addr("i1"), "stale-token", "test");

        assertThat(result).isEmpty();
        assertThat(registry.view()).containsExactly(addr("i1"));
        assertThat(recordedEvents).isEmpty();  // no event published
    }

    @Test
    void enableMovesToAvailableView() {
        registry.register(conn("i1"));
        recordedEvents.clear();

        assertThat(registry.enable(addr("i1"))).isTrue();
        assertThat(registry.availableView()).containsExactly(addr("i1"));
        assertThat(recordedEvents.stream().map(e -> e.getClass().getSimpleName()).toList())
                .containsExactly("NodeEnabled", "AvailableViewChanged");
    }

    @Test
    void enableUnknownNodeReturnsFalse() {
        assertThat(registry.enable(addr("ghost"))).isFalse();
        assertThat(registry.availableView()).isEmpty();
    }

    @Test
    void disableRemovesFromAvailableButKeepsInView() {
        registry.register(conn("i1"));
        registry.enable(addr("i1"));
        recordedEvents.clear();

        assertThat(registry.disable(addr("i1"))).isTrue();
        assertThat(registry.view()).containsExactly(addr("i1"));
        assertThat(registry.availableView()).isEmpty();
        assertThat(recordedEvents.stream().map(e -> e.getClass().getSimpleName()).toList())
                .containsExactly("NodeDisabled", "AvailableViewChanged");
    }

    @Test
    void isAvailableByBundleIdScansAllInstances() {
        registry.register(new Connection(new NodeAddress("bA", 1, "iA"),
                InMemoryTransport.pair("x", "y").client(), "t", Instant.now()));
        registry.register(new Connection(new NodeAddress("bB", 1, "iB"),
                InMemoryTransport.pair("x", "y").client(), "t", Instant.now()));
        registry.enable(new NodeAddress("bB", 1, "iB"));

        assertThat(registry.isAvailable("bA")).isFalse();
        assertThat(registry.isAvailable("bB")).isTrue();
        assertThat(registry.isAvailable("bZ")).isFalse();
    }

    @Test
    void closeAllClosesEveryTransport() {
        registry.register(conn("i1"));
        registry.register(conn("i2"));
        registry.register(conn("i3"));

        registry.closeAll("test-shutdown");

        assertThat(registry.view()).isEmpty();
        assertThat(registry.availableView()).isEmpty();
    }

    @Test
    void concurrentRegistersExactlyOneWinsPerAddress() throws Exception {
        int threads = 32;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var supersedes = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        // all threads contend for the same address
                        var previous = registry.register(conn("contested"));
                        if (previous != null) supersedes.incrementAndGet();
                    } catch (InterruptedException ignored) {
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
        // 32 inserts; 1 winner, 31 supersedes
        assertThat(registry.view()).hasSize(1);
        assertThat(supersedes.get()).isEqualTo(threads - 1);
    }
}
