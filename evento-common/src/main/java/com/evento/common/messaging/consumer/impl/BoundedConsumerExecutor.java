package com.evento.common.messaging.consumer.impl;

import com.evento.common.messaging.consumer.ConsumerExecutor;
import com.evento.common.messaging.consumer.ConsumerExecutorStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default {@link ConsumerExecutor}: a semaphore-bounded façade over a delegate
 * {@link ExecutorService}.
 *
 * <h2>Why the permit is taken on the calling thread</h2>
 * <p>{@link #submit} acquires a permit <em>before</em> handing the task to the delegate.
 * Two properties fall out of that ordering, and both are load-bearing:
 *
 * <ul>
 *   <li>Once a permit is held, the delegate is guaranteed to have a free worker (permits ==
 *       worker capacity), so the task starts essentially immediately. "Admitted" and
 *       "started" collapse into the same instant, which is exactly the checkpoint trigger
 *       the consume loop needs.</li>
 *   <li>There is <b>no internal queue anywhere</b>. A caller that cannot get a permit is
 *       told so (empty result) rather than silently buffering an unbounded prefix of the
 *       event store — which is the whole point of the backpressure design.</li>
 * </ul>
 *
 * <p>The returned future is still completed by the worker as its first action rather than
 * pre-completed, so the "completes on start" contract holds even if a subclass or future
 * change introduces queueing.
 */
public final class BoundedConsumerExecutor implements ConsumerExecutor {

    private static final Logger logger = LogManager.getLogger(BoundedConsumerExecutor.class);

    private final String name;
    private final int capacity;
    private final Semaphore permits;
    private final ExecutorService delegate;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final Object idleMonitor = new Object();
    private final AtomicLong admitted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public BoundedConsumerExecutor(String name, int capacity, ExecutorService delegate) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1, got " + capacity);
        if (delegate == null) throw new IllegalArgumentException("delegate is required");
        this.name = name;
        this.capacity = capacity;
        this.permits = new Semaphore(capacity);
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public Optional<CompletableFuture<Void>> submit(Runnable task, Duration waitFor) throws InterruptedException {
        if (shutdown.get()) {
            rejected.incrementAndGet();
            return Optional.empty();
        }
        if (!permits.tryAcquire(Math.max(0L, waitFor.toMillis()), TimeUnit.MILLISECONDS)) {
            rejected.incrementAndGet();
            return Optional.empty();
        }
        // Re-check: shutdown may have been requested while we waited for a permit.
        if (shutdown.get()) {
            permits.release();
            rejected.incrementAndGet();
            return Optional.empty();
        }
        admitted.incrementAndGet();

        // Count from admission, not from the worker's first instruction: a task that has a
        // permit but has not yet been scheduled must still make awaitQuiescence block,
        // otherwise a drain could return while work is about to run.
        inFlight.incrementAndGet();
        var started = new CompletableFuture<Void>();
        try {
            delegate.execute(() -> {
                started.complete(null);
                try {
                    task.run();
                } catch (Throwable t) {
                    // The consume loop wraps every task body in its own try/catch, so
                    // reaching here means the wrapper itself failed. Never let it escape
                    // into the delegate's uncaught-exception path, where it would be
                    // invisible.
                    failed.incrementAndGet();
                    logger.error("consumer executor '{}' task failed outside its wrapper", name, t);
                } finally {
                    completed.incrementAndGet();
                    inFlight.decrementAndGet();
                    permits.release();
                    synchronized (idleMonitor) {
                        idleMonitor.notifyAll();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // Delegate torn down underneath us. The task never ran, so honour the
            // contract: an empty result must mean "will never execute".
            inFlight.decrementAndGet();
            admitted.decrementAndGet();
            rejected.incrementAndGet();
            permits.release();
            return Optional.empty();
        }
        return Optional.of(started);
    }

    @Override
    public int inFlight() {
        return inFlight.get();
    }

    @Override
    public ConsumerExecutorStats stats() {
        return new ConsumerExecutorStats(name, capacity, inFlight.get(),
                admitted.get(), rejected.get(), completed.get(), failed.get());
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
            if (!awaitQuiescence(deadline)) {
                logger.warn("consumer executor '{}' still had {} task(s) in flight after {} — "
                                + "those events are already checkpointed and will not be redelivered",
                        name, inFlight.get(), deadline);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        delegate.shutdownNow();
    }
}
