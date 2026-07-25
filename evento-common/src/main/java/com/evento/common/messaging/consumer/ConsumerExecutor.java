package com.evento.common.messaging.consumer;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A named, bounded execution resource that consumers dispatch event handling to.
 *
 * <p>Referenced from
 * {@link com.evento.common.modeling.annotations.handler.EventHandler#executor()} by name;
 * instances are registered on the bundle builder. A named executor is a <em>shared capacity
 * budget for the whole bundle</em> — two projectors naming {@code "read-model"} compete for the
 * same permits. That is deliberate: it lets an operator cap total concurrency against a
 * downstream resource in one place.
 *
 * <h2>Why not {@link java.util.concurrent.Executor}</h2>
 * <p>The consume loop needs three things a plain {@code Executor} cannot express:
 * <ul>
 *   <li><b>"Has it started?"</b> — the consumer checkpoint advances when a task
 *       <em>begins executing</em>, not when it is enqueued. That is what bounds the
 *       crash-loss window to the set of running tasks instead of the queue depth.</li>
 *   <li><b>Bounded, time-boxed admission</b> — {@link #submit} blocks until capacity frees
 *       and gives up after {@code waitFor}, so a saturated executor ends the consume cycle
 *       (releasing the consumer lock, and with it any pinned pooled JDBC connection) rather
 *       than parking on it.</li>
 *   <li><b>Quiescence</b> — projectors must drain before declaring head reached, and the
 *       bundle must drain before shutting down; without that a graceful stop would discard
 *       work that has already been checkpointed.</li>
 * </ul>
 *
 * <h2>Contract</h2>
 * <p>Implementations MUST guarantee that {@link #submit} returning
 * {@link Optional#empty()} means the task will <em>never</em> run — the consume loop relies
 * on this to decide it is safe not to advance the checkpoint.
 *
 * <p>Implementations must be thread-safe: one executor is submitted to concurrently by every
 * consumer that names it.
 *
 * @see ConsumerExecutors
 */
public interface ConsumerExecutor extends AutoCloseable {

    /** The name handlers reference via {@code @EventHandler(executor = "...")}. */
    String name();

    /**
     * Maximum number of tasks that may execute concurrently, or
     * {@link Integer#MAX_VALUE} when unbounded.
     */
    int capacity();

    /**
     * Admit {@code task} for execution, waiting up to {@code waitFor} for capacity.
     *
     * @return a future completing when the task has <b>begun executing</b> (not when it
     *         finishes), or {@link Optional#empty()} if no capacity became available within
     *         {@code waitFor} — in which case the task is guaranteed never to run.
     * @throws InterruptedException if interrupted while waiting for capacity.
     */
    Optional<CompletableFuture<Void>> submit(Runnable task, Duration waitFor) throws InterruptedException;

    /**
     * Tasks admitted (permit granted) and not yet finished. Counted from admission rather
     * than from the first instruction of the task body so that a drain cannot slip between
     * the two and conclude the executor is idle.
     */
    int inFlight();

    /**
     * Counters for dashboards and alerting. The default reports only what the base
     * interface can observe; the shipped implementations override with real totals.
     */
    default ConsumerExecutorStats stats() {
        return new ConsumerExecutorStats(name(), capacity(), inFlight(), 0L, 0L, 0L, 0L);
    }

    /**
     * Block until every started task has finished.
     *
     * <p>Note this is executor-wide. Draining a <em>single consumer</em> (the
     * head-reached gate) is tracked by {@link ConsumerProcessor#awaitConsumerQuiescence}
     * instead, because a shared executor may be busy with other consumers' work that the
     * caller must not wait for.
     *
     * @return {@code true} if it went idle within the deadline.
     */
    boolean awaitQuiescence(Duration deadline) throws InterruptedException;

    /**
     * Stop accepting new work and await in-flight completion up to {@code deadline}.
     * Idempotent. After this returns, {@link #submit} yields {@link Optional#empty()}.
     */
    void shutdown(Duration deadline);

    @Override
    default void close() {
        shutdown(Duration.ofSeconds(30));
    }
}
