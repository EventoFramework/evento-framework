package com.evento.transport.message;

import com.evento.transport.Frame;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The caller's deadline as a receiver derives it: from the sender's remaining budget
 * ({@code timeoutMillis}) and the local arrival time, never from the sender's clock.
 */
class RequestDeadlineTest {

    private static Request request(long timeoutMillis, long senderClockMs) {
        return new Request(UUID.randomUUID(), "b", "i", "1", "T", new byte[0], timeoutMillis, senderClockMs);
    }

    @Test
    void theDeadlineIsTheArrivalPlusTheSendersBudget() {
        assertThat(request(500, 0).deadlineFrom(10_000)).isEqualTo(10_500);
    }

    @Test
    void aSkewedSenderClockDoesNotMoveTheDeadline() {
        // A sender 40 s ahead or behind: same budget, same local deadline.
        assertThat(request(500, System.currentTimeMillis() + 40_000).deadlineFrom(10_000)).isEqualTo(10_500);
        assertThat(request(500, System.currentTimeMillis() - 40_000).deadlineFrom(10_000)).isEqualTo(10_500);
    }

    @Test
    void noTimeoutMeansNoDeadline() {
        assertThat(request(0, 0).deadlineFrom(10_000)).isEqualTo(Long.MAX_VALUE);
        assertThat(request(-1, 0).deadlineFrom(10_000)).isEqualTo(Long.MAX_VALUE);
        assertThat(request(Long.MAX_VALUE, 0).deadlineFrom(10_000)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void aFrameRecordsWhenItWasReceived() {
        long before = System.currentTimeMillis();
        var frame = new Frame(request(1, 0), new byte[0]);
        assertThat(frame.receivedAtMs()).isBetween(before, System.currentTimeMillis());
        assertThat(new Frame(request(1, 0), new byte[0], 42L).receivedAtMs()).isEqualTo(42L);
    }
}
