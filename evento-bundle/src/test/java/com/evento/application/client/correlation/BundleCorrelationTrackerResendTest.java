package com.evento.application.client.correlation;

import com.evento.transport.SendFailedException;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotent requests (reads) still pending when their session ends are sent again on
 * the next one: no broker keeps their reply for a caller that went away.
 */
class BundleCorrelationTrackerResendTest {

    private final BundleCorrelationTracker tracker = new BundleCorrelationTracker(Duration.ofSeconds(60));

    @AfterEach
    void tearDown() {
        tracker.shutdown(Duration.ZERO);
    }

    private static Request read(UUID id, long timeoutMs) {
        return new Request(id, "web", "web-1", "1", "FindOrders", new byte[]{7}, timeoutMs,
                System.currentTimeMillis());
    }

    @Test
    void aReadFromAnEarlierSessionIsSentAgainWithTheSameIdAndTheTimeItHasLeft() throws Exception {
        var id = UUID.randomUUID();
        var future = tracker.track(id, "FindOrders", Duration.ofSeconds(30), "broker-a", read(id, 30_000), 1);
        var sent = new ArrayList<Request>();

        int resent = tracker.resendSentBefore(2, r -> { sent.add(r); return CompletableFuture.completedFuture(null); });

        assertThat(resent).isEqualTo(1);
        assertThat(sent).singleElement().satisfies(r -> {
            assertThat(r.correlationId()).isEqualTo(id);
            assertThat(r.payload()).containsExactly(7);
            assertThat(r.timeoutMillis()).isBetween(1L, 30_000L);
        });
        // Sent once per session, not on every READY of the same one.
        assertThat(tracker.resendSentBefore(2, r -> { sent.add(r); return null; })).isZero();

        tracker.complete(Response.success(id, "FindOrders", new byte[]{1}));
        assertThat(future.get().isError()).isFalse();
    }

    @Test
    void requestsWithSideEffectsAreNeverSentAgain() {
        tracker.track(UUID.randomUUID(), "PlaceOrder", Duration.ofSeconds(30), "broker-a", null, 1);
        assertThat(tracker.resendSentBefore(2, r -> { throw new AssertionError("resent " + r); })).isZero();
    }

    @Test
    void aReadOnTheCurrentSessionIsLeftAlone() {
        var id = UUID.randomUUID();
        tracker.track(id, "FindOrders", Duration.ofSeconds(30), "broker-a", read(id, 30_000), 2);
        assertThat(tracker.resendSentBefore(2, r -> { throw new AssertionError("resent " + r); })).isZero();
    }

    @Test
    void aReadIsNotFailedByABrokerChangeItIsAskedAgainThere() {
        var id = UUID.randomUUID();
        var future = tracker.track(id, "FindOrders", Duration.ofSeconds(30), "broker-a", read(id, 30_000), 1);
        assertThat(tracker.failSentOnOtherBroker("broker-b")).isZero();
        assertThat(future).isNotDone();
        assertThat(tracker.resendSentBefore(2, r -> CompletableFuture.completedFuture(null))).isEqualTo(1);
    }

    @Test
    void aResendThatCannotGoOutIsTriedAgainOnTheNextSession() {
        var id = UUID.randomUUID();
        tracker.track(id, "FindOrders", Duration.ofSeconds(30), "broker-a", read(id, 30_000), 1);
        assertThat(tracker.resendSentBefore(2, r -> { throw new SendFailedException("not ready", null); })).isZero();
        assertThat(tracker.resendSentBefore(3, r -> CompletableFuture.completedFuture(null))).isEqualTo(1);
    }
}
