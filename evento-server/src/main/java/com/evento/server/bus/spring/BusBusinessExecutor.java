package com.evento.server.bus.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The bus business executor: a bounded platform-thread pool that grows to
 * {@code max} <em>before</em> it queues.
 *
 * <p>A plain {@link ThreadPoolExecutor} does the opposite. It only starts a
 * thread beyond {@code core} once the work queue is <em>full</em>, so a pool
 * configured {@code core=16, max=64, queue=1024} runs on 16 threads until 1024
 * requests are backed up. Every request behind that queue waits for the 1024
 * ahead of it, and since each carries a client deadline (30s by default), they
 * expire in the queue instead of being served. Throughput does not degrade
 * gracefully under that arrangement — it collapses, and the extra 48 threads
 * the operator configured are never used.
 *
 * <p>{@link GrowthFirstQueue} inverts the order by refusing an offer while the
 * pool can still grow, which makes the executor add a thread. Only once the
 * pool is at {@code max} do requests actually queue, and only once the queue is
 * also full does {@code CallerRunsPolicy} push back on the Netty event loop —
 * which is where backpressure belongs, because it stops us reading more from
 * the socket and propagates the pressure to the client over TCP.
 *
 * <p>Reaching that fallback means demand has exceeded everything this server
 * can do, so it is counted ({@link #saturatedCount()}) and logged, throttled to
 * one line per {@code warnInterval}. That counter is the signal an operator
 * should alert on: a non-zero and climbing value means incoming request rate is
 * above capacity and clients are about to start seeing timeouts.
 */
public final class BusBusinessExecutor extends ThreadPoolExecutor {

    private static final Logger log = LoggerFactory.getLogger(BusBusinessExecutor.class);

    /**
     * Tracks submitted-but-not-yet-finished tasks so the queue can decide
     * whether a worker is free without calling {@link #getActiveCount()},
     * which locks the pool on every submission.
     */
    private final AtomicInteger submitted = new AtomicInteger();
    private final AtomicLong saturated = new AtomicLong();
    private final AtomicLong lastWarnAtMs = new AtomicLong();
    private final long warnIntervalMs;

    BusBusinessExecutor(int core,
                        int max,
                        long keepAliveMs,
                        GrowthFirstQueue queue,
                        ThreadFactory factory,
                        long warnIntervalMs) {
        super(core, max, keepAliveMs, TimeUnit.MILLISECONDS, queue, factory, new SaturationPolicy());
        this.warnIntervalMs = warnIntervalMs;
        queue.bind(this);
    }

    @Override
    public void execute(Runnable command) {
        submitted.incrementAndGet();
        try {
            super.execute(command);
        } catch (RejectedExecutionException e) {
            submitted.decrementAndGet();
            throw e;
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        submitted.decrementAndGet();
        super.afterExecute(r, t);
    }

    /** Submitted tasks not yet completed — queued plus running. */
    public int submittedCount() {
        return submitted.get();
    }

    /** Times demand exceeded pool + queue and the caller had to run the task itself. */
    public long saturatedCount() {
        return saturated.get();
    }

    public int queueDepth() {
        return getQueue().size();
    }

    private void onSaturated() {
        long count = saturated.incrementAndGet();
        long now = System.currentTimeMillis();
        long last = lastWarnAtMs.get();
        if (now - last >= warnIntervalMs && lastWarnAtMs.compareAndSet(last, now)) {
            log.warn("event=bus_business_executor_saturated total={} poolSize={} max={} queueDepth={} "
                            + "submitted={} hint=incoming request rate exceeds capacity; "
                            + "raise evento.server.bus.business-executor-max-size or reduce client concurrency",
                    count, getPoolSize(), getMaximumPoolSize(), queueDepth(), submitted.get());
        }
    }

    /**
     * Runs the task on the calling thread (as {@code CallerRunsPolicy} does) but
     * first tries to enqueue it, because {@link GrowthFirstQueue} rejects offers
     * to force pool growth rather than because the queue is genuinely full.
     */
    private static final class SaturationPolicy implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("bus business executor is shut down");
            }
            if (executor.getQueue() instanceof GrowthFirstQueue q && q.enqueue(r)) {
                return; // pool was at max: a normal queueing, not saturation
            }
            if (executor instanceof BusBusinessExecutor self) {
                self.onSaturated();
            }
            r.run();
        }
    }

    /**
     * Work queue that refuses an offer while the pool may still grow, so the
     * executor prefers starting a thread over queueing. Mirrors the approach
     * Tomcat uses for its request executor.
     */
    public static final class GrowthFirstQueue extends LinkedBlockingQueue<Runnable> {

        private transient volatile BusBusinessExecutor pool;

        public GrowthFirstQueue(int capacity) {
            super(capacity);
        }

        void bind(BusBusinessExecutor pool) {
            this.pool = pool;
        }

        /** Enqueue bypassing the growth-first rule; used by the rejection handler. */
        boolean enqueue(Runnable r) {
            return super.offer(r);
        }

        @Override
        public boolean offer(Runnable r) {
            var p = pool;
            if (p == null) {
                return super.offer(r);
            }
            int poolSize = p.getPoolSize();
            if (poolSize == p.getMaximumPoolSize()) {
                return super.offer(r); // nothing left to grow: queue normally
            }
            if (p.submittedCount() <= poolSize) {
                return super.offer(r); // a worker is free to pick this up
            }
            return false; // room to grow: make the executor add a thread
        }
    }
}
