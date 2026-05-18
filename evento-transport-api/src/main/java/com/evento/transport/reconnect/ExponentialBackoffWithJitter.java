package com.evento.transport.reconnect;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with full ± jitter:
 * delay = min(maxBackoff, base × 2^(attempt-1)) × (1 ± jitterRatio)
 *
 * Defaults: base=500ms, max=30s, jitter=0.2, unbounded retry.
 */
public final class ExponentialBackoffWithJitter implements ReconnectStrategy {

    public static final Duration DEFAULT_BASE = Duration.ofMillis(500);
    public static final Duration DEFAULT_MAX = Duration.ofSeconds(30);
    public static final double DEFAULT_JITTER_RATIO = 0.2;
    public static final int UNBOUNDED = -1;

    private final long baseMillis;
    private final long maxMillis;
    private final double jitterRatio;
    private final int maxAttempts;

    public ExponentialBackoffWithJitter() {
        this(DEFAULT_BASE, DEFAULT_MAX, DEFAULT_JITTER_RATIO, UNBOUNDED);
    }

    public ExponentialBackoffWithJitter(Duration base, Duration max, double jitterRatio, int maxAttempts) {
        if (base.isNegative() || base.isZero()) {
            throw new IllegalArgumentException("base must be positive");
        }
        if (max.compareTo(base) < 0) {
            throw new IllegalArgumentException("max must be >= base");
        }
        if (jitterRatio < 0 || jitterRatio > 1) {
            throw new IllegalArgumentException("jitterRatio must be in [0,1]");
        }
        this.baseMillis = base.toMillis();
        this.maxMillis = max.toMillis();
        this.jitterRatio = jitterRatio;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public Duration nextDelay(int attempt) {
        if (attempt < 1) attempt = 1;
        // Cap exponent to avoid overflow (2^62 < Long.MAX_VALUE).
        int shift = Math.min(attempt - 1, 62);
        long exponential = (shift >= 62) ? Long.MAX_VALUE : baseMillis << shift;
        long capped = Math.min(maxMillis, exponential);
        if (jitterRatio == 0.0) {
            return Duration.ofMillis(capped);
        }
        double factor = 1.0 + ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio);
        long withJitter = Math.max(1L, (long) (capped * factor));
        return Duration.ofMillis(withJitter);
    }

    @Override
    public boolean shouldGiveUp(int attempt) {
        return maxAttempts != UNBOUNDED && attempt > maxAttempts;
    }
}
