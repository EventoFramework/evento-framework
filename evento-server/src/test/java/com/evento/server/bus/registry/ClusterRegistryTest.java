package com.evento.server.bus.registry;

import com.evento.server.bus.NodeAddress;
import com.evento.server.bus.event.BusEventBus;
import com.evento.transport.inmemory.InMemoryTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterRegistryTest {

    private ConnectionRegistry connections;
    private ClusterRegistry cluster;

    @BeforeEach
    void setUp() {
        connections = new ConnectionRegistry(new BusEventBus());
        cluster = new ClusterRegistry(connections);
    }

    private NodeAddress addr(String inst) { return new NodeAddress("b", 1L, inst); }

    private void registerAndEnable(NodeAddress addr) {
        connections.register(new Connection(addr,
                InMemoryTransport.pair("c", "s").client(),
                UUID.randomUUID().toString(), Instant.now()));
        connections.enable(addr);
    }

    @Test
    void availableNodesForUnknownPayloadIsEmpty() {
        assertThat(cluster.availableNodesFor("com.unknown")).isEmpty();
        assertThat(cluster.pick("com.unknown")).isEmpty();
    }

    @Test
    void registeredNodeShowsUpInAvailableNodes() {
        registerAndEnable(addr("i1"));
        cluster.registerHandler(addr("i1"), "com.CreateDemo");

        assertThat(cluster.availableNodesFor("com.CreateDemo")).containsExactly(addr("i1"));
    }

    @Test
    void disabledNodeIsExcludedFromAvailable() {
        registerAndEnable(addr("i1"));
        cluster.registerHandler(addr("i1"), "com.X");
        connections.disable(addr("i1"));

        assertThat(cluster.availableNodesFor("com.X")).isEmpty();
        assertThat(cluster.knownPayloadTypes()).contains("com.X");  // still registered, just not available
    }

    @Test
    void removeNodeStripsFromAllPayloads() {
        registerAndEnable(addr("i1"));
        cluster.registerHandlers(addr("i1"), List.of("com.A", "com.B", "com.C"));

        cluster.removeNode(addr("i1"));

        assertThat(cluster.availableNodesFor("com.A")).isEmpty();
        assertThat(cluster.availableNodesFor("com.B")).isEmpty();
        assertThat(cluster.availableNodesFor("com.C")).isEmpty();
        assertThat(cluster.knownPayloadTypes()).isEmpty();  // compacted
    }

    @Test
    void pickFirstIsDeterministic() {
        registerAndEnable(addr("i1"));
        cluster.registerHandler(addr("i1"), "com.X");

        for (int i = 0; i < 10; i++) {
            assertThat(cluster.pick("com.X", ClusterRegistry.PickStrategy.FIRST))
                    .contains(addr("i1"));
        }
    }

    @Test
    void pickRandomDistributesAcrossNodes() {
        registerAndEnable(addr("i1"));
        registerAndEnable(addr("i2"));
        registerAndEnable(addr("i3"));
        cluster.registerHandlers(addr("i1"), List.of("com.X"));
        cluster.registerHandlers(addr("i2"), List.of("com.X"));
        cluster.registerHandlers(addr("i3"), List.of("com.X"));

        var distribution = new HashMap<NodeAddress, Integer>();
        for (int i = 0; i < 1000; i++) {
            var picked = cluster.pick("com.X").orElseThrow();
            distribution.merge(picked, 1, Integer::sum);
        }
        // every node should be hit at least once with 1000 samples / 3 nodes
        assertThat(distribution.keySet()).containsExactlyInAnyOrder(addr("i1"), addr("i2"), addr("i3"));
        for (var count : distribution.values()) {
            assertThat(count).isGreaterThan(100);  // statistically reasonable lower bound
        }
    }

    @Test
    void handlerCountTracksRegistrations() {
        registerAndEnable(addr("i1"));
        registerAndEnable(addr("i2"));
        cluster.registerHandler(addr("i1"), "com.X");
        cluster.registerHandler(addr("i2"), "com.X");
        cluster.registerHandler(addr("i1"), "com.Y");

        assertThat(cluster.handlerCount("com.X")).isEqualTo(2);
        assertThat(cluster.handlerCount("com.Y")).isEqualTo(1);
        assertThat(cluster.handlerCount("com.Z")).isZero();
    }
}
