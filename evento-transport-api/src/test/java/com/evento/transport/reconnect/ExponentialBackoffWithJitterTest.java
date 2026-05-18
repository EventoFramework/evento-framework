package com.evento.transport.reconnect;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExponentialBackoffWithJitterTest {

    @Test
    void delayGrowsExponentiallyUntilMax() {
        // Zero jitter for deterministic check.
        var strategy = new ExponentialBackoffWithJitter(
                Duration.ofMillis(100),
                Duration.ofMillis(800),
                0.0,
                ExponentialBackoffWithJitter.UNBOUNDED
        );
        assertThat(strategy.nextDelay(1).toMillis()).isEqualTo(100);
        assertThat(strategy.nextDelay(2).toMillis()).isEqualTo(200);
        assertThat(strategy.nextDelay(3).toMillis()).isEqualTo(400);
        assertThat(strategy.nextDelay(4).toMillis()).isEqualTo(800);
        assertThat(strategy.nextDelay(5).toMillis()).isEqualTo(800);
        assertThat(strategy.nextDelay(100).toMillis()).isEqualTo(800);
    }

    @Test
    void delayStaysWithinJitterEnvelope() {
        var strategy = new ExponentialBackoffWithJitter(
                Duration.ofMillis(1000),
                Duration.ofSeconds(30),
                0.2,
                ExponentialBackoffWithJitter.UNBOUNDED
        );
        for (int i = 0; i < 500; i++) {
            long ms = strategy.nextDelay(1).toMillis();
            assertThat(ms).isBetween(800L, 1200L);
        }
    }

    @Test
    void shouldGiveUpWhenAttemptsExceeded() {
        var strategy = new ExponentialBackoffWithJitter(
                Duration.ofMillis(100), Duration.ofSeconds(1), 0.0, 3);
        assertThat(strategy.shouldGiveUp(1)).isFalse();
        assertThat(strategy.shouldGiveUp(3)).isFalse();
        assertThat(strategy.shouldGiveUp(4)).isTrue();
    }

    @Test
    void unboundedNeverGivesUp() {
        var strategy = new ExponentialBackoffWithJitter(
                Duration.ofMillis(100), Duration.ofSeconds(1), 0.0,
                ExponentialBackoffWithJitter.UNBOUNDED);
        assertThat(strategy.shouldGiveUp(1_000_000)).isFalse();
    }

    @Test
    void invalidParametersRejected() {
        assertThatThrownBy(() ->
                new ExponentialBackoffWithJitter(Duration.ZERO, Duration.ofSeconds(1), 0.0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new ExponentialBackoffWithJitter(Duration.ofSeconds(2), Duration.ofSeconds(1), 0.0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new ExponentialBackoffWithJitter(Duration.ofMillis(100), Duration.ofSeconds(1), 1.5, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void largeAttemptCountDoesNotOverflow() {
        var strategy = new ExponentialBackoffWithJitter(
                Duration.ofMillis(100), Duration.ofSeconds(30), 0.0,
                ExponentialBackoffWithJitter.UNBOUNDED);
        long delay = strategy.nextDelay(Integer.MAX_VALUE).toMillis();
        assertThat(delay).isEqualTo(30_000L);
    }

    @Test
    void zeroOrNegativeAttemptTreatedAsOne() {
        var strategy = new ExponentialBackoffWithJitter(
                Duration.ofMillis(100), Duration.ofSeconds(1), 0.0,
                ExponentialBackoffWithJitter.UNBOUNDED);
        assertThat(strategy.nextDelay(0).toMillis()).isEqualTo(100);
        assertThat(strategy.nextDelay(-5).toMillis()).isEqualTo(100);
    }
}
