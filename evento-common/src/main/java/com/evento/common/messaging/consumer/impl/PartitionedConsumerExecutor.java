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
 * A {@link ConsumerExecutor} that preserves order per key while running different keys in
 * parallel.
 *
 * <p>Unordered parallel consumption is only safe for handlers whose effect is idempotent or
 * a blind overwrite, because two events on the same aggregate may be applied out of sequence
 * order. Partitioning removes exactly that hazard: events sharing an ordering key — the
 * aggregate id, as passed by the consume loop — are pinned to one lane and applied in
 * order, while events on different aggregates proceed concurrently. That covers the large
 * middle ground of read-model projectors that are <em>not</em> idempotent but are
 * per-aggregate sequential.
 *
 * <h2>One task per lane, never a queue</h2>
 * <p>Each lane admits a single task at a time. A submission whose lane is busy waits for it,
 * so the consume loop naturally blocks on the ordering dependency rather than buffering
 * behind it. This keeps the two invariants the rest of the design rests on: admission still
 * means "started", and there is no internal queue that could grow without bound. The cost is
 * that a burst on one hot aggregate serialises — which is precisely the ordering guarantee
 * being asked for.
 *
 * <p>Concurrency is therefore bounded by the lane count, and — as with any partitioning —
 * skewed keys reduce effective parallelism below it.
 */
public final class PartitionedConsumerExecutor implements ConsumerExecutor {

    private static final Logger logger = LogManager.getLogger(PartitionedConsumerExecutor.class);

    private final String name;
    private final Semaphore[] lanes;
    private final ExecutorService delegate;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final Object idleMonitor = new Object();
    private final AtomicInteger roundRobin = new AtomicInteger();
    private final AtomicLong admitted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public PartitionedConsumerExecutor(String name, int laneCount, ExecutorService delegate) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (laneCount < 1) throw new IllegalArgumentException("laneCount must be >= 1, got " + laneCount);
        if (delegate == null) throw new IllegalArgumentException("delegate is required");
        this.name = name;
        this.lanes = new Semaphore[laneCount];
        for (int i = 0; i < laneCount; i++) lanes[i] = new Semaphore(1);
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int capacity() {
        return lanes.length;
    }

    @Override
    public Optional<CompletableFuture<Void>> submit(Runnable task, Duration waitFor) throws InterruptedException {
        return submit(task, null, waitFor);
    }

    @Override
    public Optional<CompletableFuture<Void>> submit(Runnable task, String orderingKey, Duration waitFor)
            throws InterruptedException {
        if (shutdown.get()) {
            rejected.incrementAndGet();
            return Optional.empty();
        }

        var lane = lanes[laneFor(orderingKey)];
        if (!lane.tryAcquire(Math.max(0L, waitFor.toMillis()), TimeUnit.MILLISECONDS)) {
            rejected.incrementAndGet();
            return Optional.empty();
        }
        if (shutdown.get()) {
            lane.release();
            rejected.incrementAndGet();
            return Optional.empty();
        }
        admitted.incrementAndGet();
        inFlight.incrementAndGet();

        var started = new CompletableFuture<Void>();
        try {
            delegate.execute(() -> {
                started.complete(null);
                try {
                    task.run();
                } catch (Throwable t) {
                    failed.incrementAndGet();
                    logger.error("consumer executor '{}' task failed outside its wrapper", name, t);
                } finally {
                    completed.incrementAndGet();
                    inFlight.decrementAndGet();
                    lane.release();
                    synchronized (idleMonitor) {
                        idleMonitor.notifyAll();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            inFlight.decrementAndGet();
            admitted.decrementAndGet();
            rejected.incrementAndGet();
            lane.release();
            return Optional.empty();
        }
        return Optional.of(started);
    }

    /**
     * Events with no aggregate have no ordering relationship to preserve, so they are spread
     * round-robin rather than all colliding on lane 0 — which would serialise them for no
     * reason.
     */
    private int laneFor(String orderingKey) {
        if (orderingKey == null || orderingKey.isBlank()) {
            return Math.floorMod(roundRobin.getAndIncrement(), lanes.length);
        }
        return Math.floorMod(orderingKey.hashCode(), lanes.length);
    }

    @Override
    public int inFlight() {
        return inFlight.get();
    }

    @Override
    public ConsumerExecutorStats stats() {
        return new ConsumerExecutorStats(name, lanes.length, inFlight.get(),
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
                logger.warn("consumer executor '{}' still had {} task(s) in flight after {}",
                        name, inFlight.get(), deadline);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        delegate.shutdownNow();
    }
}
