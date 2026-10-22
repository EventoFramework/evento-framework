package com.evento.application.client;

import com.evento.application.client.correlation.BundleCorrelationTracker;
import com.evento.application.client.dedup.ProcessedRequestCache;
import com.evento.application.client.handler.HandlerRegistry;
import com.evento.transport.RequestExpiredException;
import com.evento.transport.TooManyPendingRequestsException;
import com.evento.transport.message.Message;
import com.evento.transport.message.Request;
import com.evento.transport.message.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F1: a request whose caller's deadline passed while it waited on this bundle is answered
 * without running its handler, and with a bound on the requests handled at once one more is
 * refused at once. Neither answer is cached: a retry of the same request runs.
 */
class InboundDeadlineAndAdmissionTest {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final LinkedBlockingQueue<Message> sent = new LinkedBlockingQueue<>();
    private final HandlerRegistry handlers = new HandlerRegistry();
    private final InboundDispatcher dispatcher = new InboundDispatcher(new BundleCorrelationTracker(),
            new ProcessedRequestCache(), handlers, executor, m -> {
        sent.add(m);
        return CompletableFuture.completedFuture(null);
    });

    @AfterEach
    void stop() {
        executor.shutdownNow();
    }

    private static Request request(UUID id, String payloadType, long timeoutMillis) {
        return new Request(id, "caller", "caller-1", "1", payloadType, new byte[0], timeoutMillis,
                System.currentTimeMillis());
    }

    private Response reply() throws InterruptedException {
        var message = sent.poll(5, TimeUnit.SECONDS);
        assertThat(message).isInstanceOf(Response.class);
        return (Response) message;
    }

    @Test
    void aRequestWhoseCallerStoppedWaitingWhileItQueuedIsNotRun() throws Exception {
        var gate = new CountDownLatch(1);
        var runs = new AtomicInteger();
        // A single-threaded bundle: the request queues behind a slow one.
        var single = Executors.newSingleThreadExecutor();
        var slowFirst = new InboundDispatcher(new BundleCorrelationTracker(), new ProcessedRequestCache(), handlers,
                single, m -> {
            sent.add(m);
            return CompletableFuture.completedFuture(null);
        });
        handlers.registerRequestHandler("Slow", (payload, ctx) -> {
            gate.await();
            return new byte[0];
        });
        handlers.registerRequestHandler("Pay", (payload, ctx) -> {
            runs.incrementAndGet();
            return new byte[0];
        });
        try {
            slowFirst.asMessageSink().accept(request(UUID.randomUUID(), "Slow", 0));
            var pay = UUID.randomUUID();
            slowFirst.asMessageSink().accept(request(pay, "Pay", 50));
            Thread.sleep(150);
            gate.countDown();

            reply(); // Slow
            var expired = reply();
            assertThat(expired.correlationId()).isEqualTo(pay);
            assertThat(expired.isError()).isTrue();
            assertThat(expired.error().exceptionClassName()).isEqualTo(RequestExpiredException.class.getName());
            assertThat(expired.error().message()).contains("not executed");
            assertThat(runs).hasValue(0);
            assertThat(slowFirst.expiredBeforeHandlerCount()).isEqualTo(1);

            // Not cached: the same request sent again runs.
            slowFirst.asMessageSink().accept(request(pay, "Pay", 5_000));
            assertThat(reply().isError()).isFalse();
            assertThat(runs).hasValue(1);
        } finally {
            single.shutdownNow();
        }
    }

    @Test
    void beyondTheBoundARequestIsRefusedAtOnceAndARetryRunsLater() throws Exception {
        var gate = new CountDownLatch(1);
        var runs = new AtomicInteger();
        handlers.registerRequestHandler("Cmd", (payload, ctx) -> {
            runs.incrementAndGet();
            gate.await();
            return new byte[0];
        });
        dispatcher.setMaxInboundRequests(1);

        dispatcher.asMessageSink().accept(request(UUID.randomUUID(), "Cmd", 5_000));
        var second = UUID.randomUUID();
        dispatcher.asMessageSink().accept(request(second, "Cmd", 5_000));

        var refused = reply();
        assertThat(refused.correlationId()).isEqualTo(second);
        assertThat(refused.error().exceptionClassName()).isEqualTo(TooManyPendingRequestsException.class.getName());
        assertThat(dispatcher.refusedBusyCount()).isEqualTo(1);

        gate.countDown();
        assertThat(reply().isError()).isFalse(); // the first one
        // The reply is handed over before the request stops counting as in flight.
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (dispatcher.inFlightRequests() > 0 && System.nanoTime() < until) Thread.sleep(5);
        dispatcher.asMessageSink().accept(request(second, "Cmd", 5_000));
        assertThat(reply().isError()).as("the retry runs").isFalse();
        assertThat(runs).hasValue(2);
    }

    @Test
    void withoutABoundOrADeadlineNothingIsRefused() throws Exception {
        handlers.registerRequestHandler("Cmd", (payload, ctx) -> new byte[0]);
        for (int i = 0; i < 20; i++) dispatcher.asMessageSink().accept(request(UUID.randomUUID(), "Cmd", 0));
        for (int i = 0; i < 20; i++) assertThat(reply().isError()).isFalse();
        assertThat(dispatcher.refusedBusyCount()).isZero();
        assertThat(dispatcher.expiredBeforeHandlerCount()).isZero();
    }
}
