package com.evento.transport.state;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.evento.transport.state.ConnectionState.*;
import static org.assertj.core.api.Assertions.assertThat;

class ConnectionStateMachineTest {

    @Test
    void initialStateIsDisconnected() {
        var sm = new ConnectionStateMachine("c1");
        assertThat(sm.current()).isEqualTo(DISCONNECTED);
    }

    @Test
    void legalTransitionSucceedsAndNotifiesListener() {
        var sm = new ConnectionStateMachine("c1");
        var captured = new ConnectionState[2];
        sm.addListener((from, to) -> { captured[0] = from; captured[1] = to; });

        assertThat(sm.compareAndTransition(DISCONNECTED, CONNECTING, "test")).isTrue();
        assertThat(sm.current()).isEqualTo(CONNECTING);
        assertThat(captured[0]).isEqualTo(DISCONNECTED);
        assertThat(captured[1]).isEqualTo(CONNECTING);
    }

    @Test
    void illegalTransitionIsRejected() {
        var sm = new ConnectionStateMachine("c1");
        // DISCONNECTED → CONNECTED is illegal: must go through CONNECTING/HANDSHAKING.
        assertThat(sm.compareAndTransition(DISCONNECTED, CONNECTED, "test")).isFalse();
        assertThat(sm.current()).isEqualTo(DISCONNECTED);
    }

    @Test
    void mismatchedExpectedFails() {
        var sm = new ConnectionStateMachine("c1");
        sm.compareAndTransition(DISCONNECTED, CONNECTING, "init");
        // expected mismatch — sm is CONNECTING, not DISCONNECTED.
        assertThat(sm.compareAndTransition(DISCONNECTED, CONNECTING, "test")).isFalse();
        assertThat(sm.current()).isEqualTo(CONNECTING);
    }

    @Test
    void terminalStateIsAbsorbing() {
        var sm = new ConnectionStateMachine("c1", CLOSED);
        assertThat(sm.compareAndTransition(CLOSED, CONNECTING, "x")).isFalse();
        assertThat(sm.current()).isEqualTo(CLOSED);
    }

    @Test
    void happyPathChain() {
        var sm = new ConnectionStateMachine("c1");
        assertThat(sm.compareAndTransition(DISCONNECTED, CONNECTING, "")).isTrue();
        assertThat(sm.compareAndTransition(CONNECTING, HANDSHAKING, "")).isTrue();
        assertThat(sm.compareAndTransition(HANDSHAKING, CONNECTED, "")).isTrue();
        assertThat(sm.compareAndTransition(CONNECTED, DEGRADED, "")).isTrue();
        assertThat(sm.compareAndTransition(DEGRADED, CONNECTED, "")).isTrue();
        assertThat(sm.compareAndTransition(CONNECTED, CLOSING, "")).isTrue();
        assertThat(sm.compareAndTransition(CLOSING, CLOSED, "")).isTrue();
        assertThat(sm.current()).isEqualTo(CLOSED);
    }

    @Test
    void concurrentTransitionsExactlyOneSucceeds() throws InterruptedException {
        var sm = new ConnectionStateMachine("c1");
        int threads = 64;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var winners = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (sm.compareAndTransition(DISCONNECTED, CONNECTING, "race")) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(winners.get()).isEqualTo(1);
        assertThat(sm.current()).isEqualTo(CONNECTING);
    }

    @Test
    void forceTransitionBypassesExpectedCheck() {
        var sm = new ConnectionStateMachine("c1");
        sm.compareAndTransition(DISCONNECTED, CONNECTING, "");
        // Force from any state to CLOSED.
        var previous = sm.forceTransition(CLOSED, "shutdown");
        assertThat(previous).isEqualTo(CONNECTING);
        assertThat(sm.current()).isEqualTo(CLOSED);
    }

    @Test
    void canSendWhenConnectedOrDegraded() {
        assertThat(CONNECTED.canSend()).isTrue();
        assertThat(DEGRADED.canSend()).isTrue();   // buffer filling but TCP still alive
        assertThat(DISCONNECTED.canSend()).isFalse();
        assertThat(CONNECTING.canSend()).isFalse();
        assertThat(HANDSHAKING.canSend()).isFalse();
        assertThat(CLOSED.canSend()).isFalse();
    }
}
