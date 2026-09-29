package com.evento.common.messaging.consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Random;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(10)
class SequenceRangeSetTest {

    @Test
    void adjacentAndOverlappingIntervalsCoalesce() {
        var s = new SequenceRangeSet();
        s.add(5, 5);
        s.add(7, 9);
        assertThat(s.intervalCount()).isEqualTo(2);
        s.add(6, 6);                              // bridges [5] and [7..9]
        assertThat(s.intervalCount()).isEqualTo(1);
        assertThat(s.toString()).isEqualTo("[5..9]");
        s.add(1, 20);                             // swallows it
        assertThat(s.toString()).isEqualTo("[1..20]");
        s.add(3, 4);                              // already covered
        assertThat(s.toString()).isEqualTo("[1..20]");
        s.add(22, 30);
        s.add(21, 21);
        assertThat(s.toString()).isEqualTo("[1..30]");
    }

    @Test
    void anEmptyIntervalIsIgnored() {
        var s = new SequenceRangeSet();
        s.add(10, 9);
        assertThat(s.isEmpty()).isTrue();
    }

    @Test
    void advanceConsumesOnlyTheContiguousRun() {
        var s = new SequenceRangeSet();
        s.add(2, 4);
        s.add(6, 8);
        assertThat(s.advance(0)).as("1 is unresolved").isZero();
        assertThat(s.advance(1)).isEqualTo(4L);
        assertThat(s.advance(4)).as("5 is unresolved").isEqualTo(4L);
        s.add(5, 5);
        assertThat(s.advance(4)).isEqualTo(8L);
        assertThat(s.isEmpty()).isTrue();
    }

    @Test
    void discardThroughClipsWithoutAdvancing() {
        var s = new SequenceRangeSet();
        s.add(2, 3);
        s.add(5, 9);
        s.add(12, 12);
        s.discardThrough(6);
        assertThat(s.toString()).isEqualTo("[7..9, 12]");
        assertThat(s.advance(6)).isEqualTo(9L);
    }

    @Test
    void gapsOfBillionsCostOneEntry() {
        var s = new SequenceRangeSet();
        long base = 7_300_000_000_000_000L;       // Snowflake-scale ids
        s.add(base + 1, base + 4_200_000_000L - 1);
        s.add(base + 4_200_000_000L, base + 4_200_000_000L);
        assertThat(s.intervalCount()).isEqualTo(1);
        assertThat(s.advance(base)).isEqualTo(base + 4_200_000_000L);
    }

    @Test
    void agreesWithANaiveSetOnRandomOperations() {
        var rnd = new Random(42);
        var s = new SequenceRangeSet();
        var naive = new TreeSet<Long>();
        long watermark = 0;
        for (int i = 0; i < 20_000; i++) {
            long from = watermark + 1 + rnd.nextInt(200);
            long to = from + rnd.nextInt(5) - 1;   // sometimes empty
            s.add(from, to);
            for (long x = from; x <= to; x++) naive.add(x);
            if (rnd.nextInt(10) == 0) {
                long w = s.advance(watermark);
                while (naive.remove(watermark + 1)) watermark++;
                naive.headSet(watermark, true).clear();
                assertThat(w).isEqualTo(watermark);
            }
            if (i % 997 == 0) {
                for (long x = watermark + 1; x < watermark + 250; x++) {
                    assertThat(s.contains(x)).as("seq %d at step %d", x, i).isEqualTo(naive.contains(x));
                }
            }
        }
    }
}
