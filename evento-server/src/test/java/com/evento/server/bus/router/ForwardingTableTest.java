package com.evento.server.bus.router;

import com.evento.server.bus.NodeAddress;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ForwardingTable}, including the {@code removeOlderThan} pruning primitive
 * that {@code BusLifecycle}'s background maintenance relies on to bound memory.
 */
class ForwardingTableTest {

    private static final NodeAddress A = new NodeAddress("a", 1, "a-1");
    private static final NodeAddress B = new NodeAddress("b", 1, "b-1");

    @Test
    void tracksAndResolvesOnce() {
        var table = new ForwardingTable();
        var id = UUID.randomUUID();

        assertThat(table.track(id, A, B, "DoThing")).isTrue();
        assertThat(table.track(id, A, B, "DoThing")).as("duplicate id rejected").isFalse();
        assertThat(table.size()).isEqualTo(1);

        var resolved = table.resolve(id);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().originator()).isEqualTo(A);
        assertThat(table.resolve(id)).as("entry removed on resolve").isEmpty();
        assertThat(table.size()).isZero();
    }

    @Test
    void removeOlderThanPrunesOnlyStaleEntries() {
        var table = new ForwardingTable();
        table.track(UUID.randomUUID(), A, B, "Old");
        // Everything tracked so far is "old" relative to a future cutoff; nothing relative to the past.
        long future = System.currentTimeMillis() + 60_000;
        long past = System.currentTimeMillis() - 60_000;

        assertThat(table.removeOlderThan(past)).as("no entry older than a past cutoff").isZero();
        assertThat(table.size()).isEqualTo(1);

        assertThat(table.removeOlderThan(future)).as("the entry is older than a future cutoff").isEqualTo(1);
        assertThat(table.size()).isZero();
    }

    @Test
    void drainByDestinationLeavesOriginatorSideEntries() {
        var table = new ForwardingTable();
        var toB = UUID.randomUUID();
        var fromB = UUID.randomUUID();
        table.track(toB, A, B, "ToB");     // B is destination
        table.track(fromB, B, A, "FromB"); // B is originator

        var drained = table.drainByDestination(B);

        assertThat(drained).extracting(ForwardingTable.Entry::correlationId).containsExactly(toB);
        assertThat(table.resolve(fromB)).as("originator-side entry survives for reconnect delivery").isPresent();
    }
}
