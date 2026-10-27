package com.evento.server.es;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the staleness gate on {@code EventFetchRequest} handling.
 *
 * <p>The gate exists because a consumer abandons a fetch after its client-side
 * timeout and immediately retries: serving the abandoned request repeats the
 * heaviest broker work (full result set + deserialize + encode) for a reply
 * nobody is waiting on, exactly when the broker is under memory pressure
 * (incident 2026-07-27). The full handler path is exercised by the bus
 * integration tests; here we pin the gate's decision logic.
 */
class EventStoreRequestHandlerBridgeTest {

    @Test
    void aFetchThatJustArrivedIsNotStale() {
        assertThat(EventStoreRequestHandlerBridge.isStale(1_000, 30_000)).isFalse();
    }

    @Test
    void aFetchThatWaitedLongerThanTheMaxAgeIsStale() {
        assertThat(EventStoreRequestHandlerBridge.isStale(30_001, 30_000)).isTrue();
    }

    @Test
    void exactlyAtTheMaxAgeIsNotStale() {
        assertThat(EventStoreRequestHandlerBridge.isStale(30_000, 30_000)).isFalse();
    }

    @Test
    void aNonPositiveMaxAgeDisablesTheGate() {
        assertThat(EventStoreRequestHandlerBridge.isStale(999_999, 0)).isFalse();
        assertThat(EventStoreRequestHandlerBridge.isStale(999_999, -1)).isFalse();
    }

    /**
     * S15: the gate used to compare the consumer's own timestamp with the server's clock, so a
     * consumer whose clock ran 40 s behind had every fetch dropped. The age is now what the fetch
     * spent on this server, which no client clock enters.
     */
    @Test
    void theConsumersClockPlaysNoPart() {
        // Whatever the consumer stamped, a fetch served 5 ms after its arrival is fresh.
        assertThat(EventStoreRequestHandlerBridge.isStale(5, 30_000)).isFalse();
    }
}
