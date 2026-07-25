package com.evento.common.messaging.consumer.impl;

import com.evento.common.messaging.consumer.ConsumerExecutor;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compatibility adapter that presents a plain {@link Executor} as a
 * {@link ConsumerExecutor} with no capacity limit.
 *
 * <p>This exists for the pre-existing
 * {@code ConsumerProcessor.Builder.observerExecutor(Executor)} entry point, whose historical
 * semantics were exactly this: hand every event to an unbounded executor and advance the
 * checkpoint regardless. Preserving it keeps existing wiring compiling and behaving as before.
 *
 * <p><b>Prefer {@link ConsumerExecutors#virtual(String, int)} for anything new.</b> Unbounded
 * fan-out gives no backpressure, so a consumer catching up from a cold checkpoint will submit
 * the entire backlog as fast as it can fetch it, and the crash-loss window grows to the whole
 * in-flight set rather than a known bound.
 *
 * <p>The delegate is <em>not</em> owned: {@link #shutdown} awaits quiescence but never shuts
 * the delegate down, since the caller may still be using it elsewhere.
 */
public final class UnboundedConsumerExecutor implements ConsumerExecutor {

    private final String name;
    private final Executor delegate;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final Object idleMonitor = new Object();

    public UnboundedConsumerExecutor(String name, Executor delegate) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (delegate == null) throw new IllegalArgumentException("delegate is required");
        this.name = name;
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int capacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Optional<CompletableFuture<Void>> submit(Runnable task, Duration waitFor) {
        if (shutdown.get()) return Optional.empty();
        var started = new CompletableFuture<Void>();
        inFlight.incrementAndGet();
        try {
            delegate.execute(() -> {
                started.complete(null);
                try {
                    task.run();
                } finally {
                    inFlight.decrementAndGet();
                    synchronized (idleMonitor) {
                        idleMonitor.notifyAll();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
            return Optional.empty();
        }
        return Optional.of(started);
    }

    @Override
    public int inFlight() {
        return inFlight.get();
    }

    @Override
    public boolean awaitQuiescence(Duration deadline) throws InterruptedException {
        long endNanos = System.nanoTime() + deadline.toNanos();
        synchronized (idleMonitor) {
            while (inFlight.get() > 0) {
                long remaining = endNanos - System.nanoTime();
                if (remaining <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(idleMonitor, remaining);
            }
        }
        return true;
    }

    @Override
    public void shutdown(Duration deadline) {
        if (!shutdown.compareAndSet(false, true)) return;
        try {
            awaitQuiescence(deadline);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Delegate intentionally left running — we do not own it.
    }
}
