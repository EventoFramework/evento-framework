package com.evento.server.service.discovery;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The presence rows of a live server must never go stale between two of its refreshes: a
 * stale-after window at or below the heartbeat interval would make every other server treat
 * that server's live instances as dead and unregister bundles that are still running (G7).
 */
class BundlePresenceTimingTest {

    @Test
    void aStaleWindowNoLongerThanTheHeartbeatIsRefusedAtBoot() {
        assertThatThrownBy(() -> BundlePresence.checkTiming(Duration.ofSeconds(30), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stale-after-ms");
        assertThatThrownBy(() -> BundlePresence.checkTiming(Duration.ofSeconds(10), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTightButWorkableWindowOnlyWarnsAndTheShippedDefaultsPass() {
        assertThatCode(() -> BundlePresence.checkTiming(Duration.ofSeconds(60), Duration.ofSeconds(30)))
                .doesNotThrowAnyException();
        assertThatCode(() -> BundlePresence.checkTiming(Duration.ofMillis(180_000), Duration.ofMillis(30_000)))
                .doesNotThrowAnyException();
    }

    @Test
    void aNonPositiveHeartbeatIsRefused() {
        assertThatThrownBy(() -> BundlePresence.checkTiming(Duration.ofSeconds(60), Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }
}
