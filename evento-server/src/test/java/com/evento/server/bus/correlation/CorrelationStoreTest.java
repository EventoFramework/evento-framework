package com.evento.server.bus.correlation;

import com.evento.server.bus.NodeAddress;
import com.evento.transport.ShutdownInProgressException;
import com.evento.transport.message.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class CorrelationStoreTest {

    private CorrelationStore store;

    @BeforeEach
    void setUp() {
        store = new CorrelationStore(Duration.ofMillis(50));
    }

    @AfterEach
    void tearDown() {
        store.shutdown(Duration.ofMillis(100));
    }

    private NodeAddress addr(String s) { return new NodeAddress("b", 1L, s); }

    @Test
    void submitAndCompleteDeliversResponse() throws Exception {
        var corr = UUID.randomUUID();
        var future = store.submit(addr("from"), addr("to"), corr, "com.X", 5000L);

        var response = Response.success(corr, "com.Y", new byte[]{1, 2, 3});
        boolean matched = store.complete(response);

        assertThat(matched).isTrue();
        assertThat(future.get(500, TimeUnit.MILLISECONDS)).isEqualTo(response);
        assertThat(store.pendingCount()).isZero();
    }

    @Test
    void completeUnknownCorrelationReturnsFalse() {
        boolean matched = store.complete(Response.success(UUID.randomUUID(), "x", new byte[0]));
        assertThat(matched).isFalse();
    }

    @Test
    void expiredCorrelationCompletesWithErrorResponse() {
        var corr = UUID.randomUUID();
        var future = store.submit(addr("from"), addr("to"), corr, "com.X", 100L);

        await().atMost(2, TimeUnit.SECONDS).until(future::isDone);
        var resp = future.join();
        assertThat(resp.isError()).isTrue();
        assertThat(resp.error().exceptionClassName())
                .isEqualTo("com.evento.transport.RequestTimeoutException");
        assertThat(store.pendingCount()).isZero();
    }

    @Test
    void zeroTimeoutMeansNoExpiry() throws Exception {
        var corr = UUID.randomUUID();
        var future = store.submit(addr("from"), addr("to"), corr, "com.X", 0L);

        Thread.sleep(200);
        assertThat(future.isDone()).isFalse();
        assertThat(store.pendingCount()).isEqualTo(1);

        store.complete(Response.success(corr, "y", new byte[0]));
        assertThat(future.get(200, TimeUnit.MILLISECONDS).isError()).isFalse();
    }

    @Test
    void failMatchingFailsOnlySelectedEntries() {
        var c1 = store.submit(addr("a"), addr("node-A"), UUID.randomUUID(), "x", 0);
        var c2 = store.submit(addr("a"), addr("node-B"), UUID.randomUUID(), "x", 0);
        var c3 = store.submit(addr("a"), addr("node-A"), UUID.randomUUID(), "x", 0);

        int count = store.failMatching(p -> p.to().equals(addr("node-A")),
                new RuntimeException("node-A gone"));

        assertThat(count).isEqualTo(2);
        assertThat(c1.isCompletedExceptionally()).isTrue();
        assertThat(c2.isCompletedExceptionally()).isFalse();
        assertThat(c3.isCompletedExceptionally()).isTrue();
    }

    @Test
    void shutdownWaitsThenForceCancelsPendingWithDeadline() {
        var c1 = store.submit(addr("a"), addr("b"), UUID.randomUUID(), "x", 0);
        var c2 = store.submit(addr("a"), addr("b"), UUID.randomUUID(), "x", 0);

        long t0 = System.currentTimeMillis();
        store.shutdown(Duration.ofMillis(200));
        long elapsed = System.currentTimeMillis() - t0;

        assertThat(elapsed).isLessThan(1000L);  // bounded by ~200ms + cleaner shutdown
        assertThat(c1.isCompletedExceptionally()).isTrue();
        assertThat(c2.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(c1::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ShutdownInProgressException.class);
    }

    @Test
    void submitAfterShutdownReturnsFailedFuture() {
        store.shutdown(Duration.ofMillis(50));
        var f = store.submit(addr("a"), addr("b"), UUID.randomUUID(), "x", 0);
        assertThat(f.isCompletedExceptionally()).isTrue();
    }
}
