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
    void freshRequestIsNotExpired() {
        long now = 1_000_000L;
        assertThat(EventStoreRequestHandlerBridge.isExpired(now - 1_000, now, 30_000)).isFalse();
    }

    @Test
    void requestOlderThanMaxAgeIsExpired() {
        long now = 1_000_000L;
        assertThat(EventStoreRequestHandlerBridge.isExpired(now - 30_001, now, 30_000)).isTrue();
    }

    @Test
    void requestExactlyAtMaxAgeIsNotExpired() {
        long now = 1_000_000L;
        assertThat(EventStoreRequestHandlerBridge.isExpired(now - 30_000, now, 30_000)).isFalse();
    }

    @Test
    void unstampedRequestDisablesTheGate() {
        // Older clients may not stamp the envelope; never drop their requests.
        assertThat(EventStoreRequestHandlerBridge.isExpired(0, 1_000_000L, 30_000)).isFalse();
        assertThat(EventStoreRequestHandlerBridge.isExpired(-1, 1_000_000L, 30_000)).isFalse();
    }

    @Test
    void nonPositiveMaxAgeDisablesTheGate() {
        long now = 1_000_000L;
        assertThat(EventStoreRequestHandlerBridge.isExpired(now - 999_999, now, 0)).isFalse();
        assertThat(EventStoreRequestHandlerBridge.isExpired(now - 999_999, now, -1)).isFalse();
    }

    @Test
    void clockSkewAheadOfServerIsNotExpired() {
        // A client clock slightly ahead of the server produces a negative age.
        long now = 1_000_000L;
        assertThat(EventStoreRequestHandlerBridge.isExpired(now + 5_000, now, 30_000)).isFalse();
    }
}
