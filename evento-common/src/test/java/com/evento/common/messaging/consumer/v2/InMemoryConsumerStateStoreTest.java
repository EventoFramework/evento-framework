package com.evento.common.messaging.consumer.v2;

import com.evento.common.messaging.consumer.v2.impl.InMemoryConsumerStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryConsumerStateStoreTest {

    private InMemoryConsumerStateStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryConsumerStateStore();
    }

    @Test
    void readReturnsEmptyForUnknownConsumer() {
        assertThat(store.read("missing")).isEmpty();
    }

    @Test
    void firstCommitMustUseVersionZeroAndReturnsOne() throws Exception {
        long v = store.commit("c1", new EventCheckpoint(10), 0L);
        assertThat(v).isEqualTo(1L);

        var read = store.read("c1");
        assertThat(read).isPresent();
        assertThat(read.get().version()).isEqualTo(1L);
        assertThat(read.get().checkpoint()).isEqualTo(new EventCheckpoint(10));
    }

    @Test
    void firstCommitWithWrongExpectedVersionFails() {
        assertThatThrownBy(() -> store.commit("c1", new EventCheckpoint(10), 42L))
                .isInstanceOf(OptimisticLockException.class)
                .satisfies(t -> {
                    var e = (OptimisticLockException) t;
                    assertThat(e.consumerId()).isEqualTo("c1");
                    assertThat(e.expectedVersion()).isEqualTo(42L);
                    assertThat(e.actualVersion()).isEqualTo(0L);
                });
        assertThat(store.read("c1")).isEmpty();
    }

    @Test
    void subsequentCommitsRequireMatchingVersion() throws Exception {
        store.commit("c1", new EventCheckpoint(1), 0L);
        store.commit("c1", new EventCheckpoint(2), 1L);

        assertThatThrownBy(() -> store.commit("c1", new EventCheckpoint(3), 1L))
                .isInstanceOf(OptimisticLockException.class);

        var read = store.read("c1");
        assertThat(read).isPresent();
        assertThat(read.get().version()).isEqualTo(2L);
        assertThat(read.get().checkpoint()).isEqualTo(new EventCheckpoint(2));
    }

    @Test
    void deleteForgetsConsumerAndAllowsFreshFirstCommit() throws Exception {
        store.commit("c1", new ProjectorCheckpoint(5), 0L);
        store.delete("c1");
        assertThat(store.read("c1")).isEmpty();

        long v = store.commit("c1", new ProjectorCheckpoint(99), 0L);
        assertThat(v).isEqualTo(1L);
    }

    @Test
    void deleteOnMissingConsumerIsNoOp() {
        store.delete("ghost");
        assertThat(store.read("ghost")).isEmpty();
    }

    @Test
    void listConsumersReturnsAllKnownIds() throws Exception {
        store.commit("a", new EventCheckpoint(1), 0L);
        store.commit("b", new SagaCheckpoint(2), 0L);
        store.commit("c", new ProjectorCheckpoint(3), 0L);

        try (var stream = store.listConsumers()) {
            assertThat(stream).containsExactlyInAnyOrder("a", "b", "c");
        }
    }

    @Test
    void differentCheckpointKindsRoundTrip() throws Exception {
        store.commit("evt", new EventCheckpoint(10), 0L);
        store.commit("saga", new SagaCheckpoint(20), 0L);
        store.commit("proj", new ProjectorCheckpoint(30), 0L);

        assertThat(store.read("evt").orElseThrow().checkpoint()).isInstanceOf(EventCheckpoint.class);
        assertThat(store.read("saga").orElseThrow().checkpoint()).isInstanceOf(SagaCheckpoint.class);
        assertThat(store.read("proj").orElseThrow().checkpoint()).isInstanceOf(ProjectorCheckpoint.class);
    }

    @Test
    void enabledFlagDefaultsToTrueAndRoundTrips() {
        assertThat(store.isEnabled("c1")).isTrue();
        store.setEnabled("c1", false);
        assertThat(store.isEnabled("c1")).isFalse();
        store.setEnabled("c1", true);
        assertThat(store.isEnabled("c1")).isTrue();
    }

    @Test
    void errorStateStartsHealthyAndPinsStartTimestampAcrossStreak() {
        Clock c = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        var s = new InMemoryConsumerStateStore(c);

        assertThat(s.getErrorState("c1")).isEqualTo(ConsumerErrorState.healthy());

        s.setLastError("c1", new RuntimeException("first"));
        var e1 = s.getErrorState("c1");
        assertThat(e1.inError()).isTrue();
        assertThat(e1.errorCount()).isEqualTo(1L);
        assertThat(e1.errorStartAt()).isEqualTo(c.instant());
        assertThat(e1.lastErrorAt()).isEqualTo(c.instant());
        assertThat(e1.errorMessage()).contains("first");

        s.setLastError("c1", new RuntimeException("second"));
        var e2 = s.getErrorState("c1");
        assertThat(e2.errorCount()).isEqualTo(2L);
        assertThat(e2.errorStartAt()).isEqualTo(c.instant()); // pinned
        assertThat(e2.errorMessage()).contains("second");
    }

    @Test
    void successfulCommitClearsErrorState() throws Exception {
        store.setLastError("c1", new RuntimeException("boom"));
        assertThat(store.getErrorState("c1").inError()).isTrue();

        store.commit("c1", new EventCheckpoint(1), 0L);
        assertThat(store.getErrorState("c1")).isEqualTo(ConsumerErrorState.healthy());
    }

    @Test
    void deleteAlsoForgetsEnabledAndErrorState() throws Exception {
        store.commit("c1", new EventCheckpoint(1), 0L);
        store.setEnabled("c1", false);
        store.setLastError("c1", new RuntimeException("boom"));

        store.delete("c1");
        assertThat(store.read("c1")).isEmpty();
        assertThat(store.isEnabled("c1")).isTrue(); // back to default
        assertThat(store.getErrorState("c1")).isEqualTo(ConsumerErrorState.healthy());
    }

    @Test
    void concurrentCommitsHaveExactlyOneWinnerPerVersion() throws Exception {
        store.commit("c1", new EventCheckpoint(0), 0L);

        int contenders = 32;
        try (ExecutorService pool = Executors.newFixedThreadPool(contenders)) {
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger conflicts = new AtomicInteger();

            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                final int payload = i + 1;
                futures.add(pool.submit(() -> {
                    try {
                        store.commit("c1", new EventCheckpoint(payload), 1L);
                        successes.incrementAndGet();
                    } catch (OptimisticLockException e) {
                        conflicts.incrementAndGet();
                    }
                }));
            }
            for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);

            assertThat(successes.get()).isEqualTo(1);
            assertThat(conflicts.get()).isEqualTo(contenders - 1);
            assertThat(store.read("c1").orElseThrow().version()).isEqualTo(2L);
        }
    }
}
