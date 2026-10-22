package com.evento.common.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * APES stores events under Snowflake ids, so an id that fails refuses the command being stored.
 * The B1 benchmark saw it on WSL2, whose clock is stepped back by the host's time sync: "Invalid
 * System Clock!" a few times a minute under load.
 */
class SnowflakeTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    void idsKeepGrowingWhenTheClockStepsBack() {
        var clock = new AtomicLong(NOW);
        var snowflake = new Snowflake(7, clock::get);

        long before = snowflake.nextId();
        clock.set(NOW - 5);
        long during = snowflake.nextId();
        long stillBehind = snowflake.nextId();
        clock.set(NOW + 1);
        long after = snowflake.nextId();

        assertThat(during).isGreaterThan(before);
        assertThat(stillBehind).isGreaterThan(during);
        assertThat(after).isGreaterThan(stillBehind);
    }

    @Test
    void anExhaustedMillisecondMovesOnWithoutWaitingForTheClock() {
        var clock = new AtomicLong(NOW);
        var snowflake = new Snowflake(7, clock::get);

        long previous = snowflake.nextId();
        // Two milliseconds' worth of sequence on a clock that never moves: it used to spin forever.
        for (int i = 0; i < 8192; i++) {
            long next = snowflake.nextId();
            assertThat(next).isGreaterThan(previous);
            previous = next;
        }
        assertThat(snowflake.parse(previous)[0]).isEqualTo(NOW + 2);
    }
}
