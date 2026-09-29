package com.evento.application.client.correlation;

import com.evento.transport.BrokerChangedException;
import com.evento.transport.RequestTimeoutException;
import com.evento.transport.message.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which pending requests a broker change ends. The rule: only those sent
 * through a broker other than the one the bundle is now attached to — their
 * replies are buffered on the old broker and cannot follow. Everything sent
 * through the current broker is left to complete (that broker replays replies
 * on reconnect).
 */
class BundleCorrelationTrackerBrokerChangeTest {

    private final BundleCorrelationTracker tracker = new BundleCorrelationTracker(Duration.ofSeconds(60));

    @AfterEach
    void tearDown() {
        tracker.shutdown(Duration.ZERO);
    }

    @Test
    void requestsSentThroughAnotherBrokerFailWithAnIndeterminateBrokerChange() throws Exception {
        var viaA = tracker.track(UUID.randomUUID(), "Cmd", Duration.ofSeconds(30), "broker-a");
        var viaB = tracker.track(UUID.randomUUID(), "Cmd", Duration.ofSeconds(30), "broker-b");

        int failed = tracker.failSentOnOtherBroker("broker-b");

        assertThat(failed).isEqualTo(1);
        Response r = viaA.get();
        assertThat(r.isError()).isTrue();
        assertThat(r.error().exceptionClassName()).isEqualTo(BrokerChangedException.class.getName());
        assertThat(r.error().message()).contains("indeterminate").contains("broker-a").contains("broker-b");
        assertThat(viaB).isNotDone();
        assertThat(tracker.pendingCount()).isEqualTo(1);
    }

    @Test
    void reconnectingToTheSameBrokerFailsNothing() {
        var viaA = tracker.track(UUID.randomUUID(), "Cmd", Duration.ofSeconds(30), "broker-a");

        assertThat(tracker.failSentOnOtherBroker("broker-a")).isZero();
        assertThat(viaA).isNotDone();
    }

    @Test
    void aRequestWithNoKnownBrokerIsNeverFailedByABrokerChange() {
        var unknown = tracker.track(UUID.randomUUID(), "Cmd", Duration.ofSeconds(30));

        assertThat(tracker.failSentOnOtherBroker("broker-b")).isZero();
        assertThat(unknown).isNotDone();
    }

    @Test
    void aBrokerChangeIsATimeoutForCallersThatOnlyKnowTimeouts() {
        // Callers that already treat a timeout as "outcome unknown" must handle
        // this one the same way without change.
        assertThat(RequestTimeoutException.class).isAssignableFrom(BrokerChangedException.class);
    }
}
