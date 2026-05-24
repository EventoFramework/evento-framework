package com.evento.common.messaging.consumer;

import com.evento.common.messaging.consumer.impl.InMemoryDeadEventQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryDeadEventQueueTest {

    @Test
    void addThenGetAllSurfacesEntry() {
        var q = new InMemoryDeadEventQueue();
        var e = TestEvents.event(10, "OrderPlaced");
        q.add("consumer-A", e, new RuntimeException("boom"));

        var all = q.getAll("consumer-A");
        assertThat(all).hasSize(1);
        var first = all.iterator().next();
        assertThat(first.getEventName()).isEqualTo("OrderPlaced");
        assertThat(first.getConsumerId()).isEqualTo("consumer-A");
        assertThat(first.isRetry()).isFalse();
        assertThat(first.getException().getMessage()).contains("boom");
    }

    @Test
    void retryFlagFlipsAndSurfacesViaGetRetriable() {
        var q = new InMemoryDeadEventQueue();
        var e = TestEvents.event(10, "OrderPlaced");
        q.add("c1", e, new RuntimeException("boom"));
        assertThat(q.getRetriable("c1")).isEmpty();

        q.setRetry("c1", 10, true);
        assertThat(q.getRetriable("c1")).hasSize(1);

        q.setRetry("c1", 10, false);
        assertThat(q.getRetriable("c1")).isEmpty();
    }

    @Test
    void removeDropsEntry() {
        var q = new InMemoryDeadEventQueue();
        var e = TestEvents.event(10, "OrderPlaced");
        q.add("c1", e, new RuntimeException("boom"));
        q.remove("c1", e);
        assertThat(q.getAll("c1")).isEmpty();
    }

    @Test
    void differentConsumersAreIndependent() {
        var q = new InMemoryDeadEventQueue();
        var e = TestEvents.event(10, "OrderPlaced");
        q.add("a", e, new RuntimeException("boom"));
        assertThat(q.getAll("a")).hasSize(1);
        assertThat(q.getAll("b")).isEmpty();
    }

    @Test
    void getAllOnUnknownConsumerIsEmpty() {
        var q = new InMemoryDeadEventQueue();
        assertThat(q.getAll("ghost")).isEmpty();
        assertThat(q.getRetriable("ghost")).isEmpty();
    }
}
