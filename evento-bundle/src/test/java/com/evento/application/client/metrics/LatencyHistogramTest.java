package com.evento.application.client.metrics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LatencyHistogramTest {

    @Test
    void bucketsCoverEveryValueAndTheirBoundsAreOrdered() {
        long previous = -1;
        for (long v : new long[]{0, 1, 31, 32, 33, 63, 64, 1_000, 999_999, 1_000_000, 123_456_789, Long.MAX_VALUE / 4}) {
            int i = LatencyHistogram.index(v);
            assertThat(LatencyHistogram.lowerBound(i)).as("lower bound of %d", v).isLessThanOrEqualTo(v);
            assertThat(LatencyHistogram.lowerBound(i + 1)).as("next bucket of %d", v).isGreaterThan(v);
            assertThat(i).isGreaterThanOrEqualTo((int) Math.max(0, previous));
            previous = i;
        }
    }

    @Test
    void percentilesAreWithinThreePercentOfTheExactOnes() {
        var h = new LatencyHistogram();
        var values = new long[10_000];
        var rnd = ThreadLocalRandom.current();
        for (int i = 0; i < values.length; i++) {
            // 0.2 ms .. 200 ms, log-uniform: the range of a command's phases
            values[i] = (long) Math.exp(Math.log(200_000) + rnd.nextDouble() * Math.log(1_000));
            h.record(values[i]);
        }
        java.util.Arrays.sort(values);
        var s = h.snapshot();
        assertThat(s.count()).isEqualTo(values.length);
        assertThat(s.maxNanos()).isEqualTo(values[values.length - 1]);
        assertThat((double) s.p50Nanos()).isCloseTo(values[4_999], within(values[4_999] * 0.03));
        assertThat((double) s.p95Nanos()).isCloseTo(values[9_499], within(values[9_499] * 0.03));
        assertThat((double) s.p99Nanos()).isCloseTo(values[9_899], within(values[9_899] * 0.03));
    }

    @Test
    void anEmptyOrResetHistogramReportsZeros() {
        var h = new LatencyHistogram();
        assertThat(h.snapshot().p99Nanos()).isZero();
        h.record(5_000_000);
        assertThat(h.count()).isEqualTo(1);
        h.reset();
        assertThat(h.snapshot()).isEqualTo(new LatencyHistogram.Snapshot(0, 0, 0, 0, 0, 0));
    }

    @Test
    void namesPastTheBoundShareOneHistogram() {
        var r = new LatencyRecorder(2);
        r.record("a", 1);
        r.record("b", 1);
        r.record("c", 1);
        r.record("d", 1);
        assertThat(r.snapshot()).containsOnlyKeys("a", "b", LatencyRecorder.OVERFLOW);
        assertThat(r.snapshot().get(LatencyRecorder.OVERFLOW).count()).isEqualTo(2);
    }
}
