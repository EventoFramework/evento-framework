package com.evento.common.messaging.consumer;

import com.evento.common.messaging.consumer.impl.BoundedConsumerExecutor;
import com.evento.common.messaging.consumer.impl.UnboundedConsumerExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Factories for the {@link ConsumerExecutor} implementations shipped with the framework.
 *
 * <pre>{@code
 * EventoBundle.Builder.builder()
 *     .addConsumerExecutor(ConsumerExecutors.virtual("read-model", 64))
 *     .addConsumerExecutor(ConsumerExecutors.pooled("tx-writer", 8))
 *     …
 * }</pre>
 *
 * <p>Then reference one by name from a handler:
 * {@code @EventHandler(executor = "read-model", retry = 3)}.
 */
public final class ConsumerExecutors {

    private ConsumerExecutors() {
    }

    /**
     * Virtual-thread executor capped at {@code maxConcurrency} simultaneously running
     * handlers. The right default for I/O-bound handlers (HTTP calls, cache writes).
     *
     * <p><b>Sizing against a JDBC pool.</b> If handlers on this executor open a database
     * transaction (typically via a {@code MessageHandlerInterceptor}), each running handler
     * holds a pooled connection for its whole task — on top of the one connection every
     * active consumer already pins through its {@code ConsumerLock}. Size the pool for
     * {@code concurrent consumers + Σ(capacity of transactional executors) + headroom}, or
     * cap this executor at its share of the pool. Under-sizing surfaces as
     * connection-acquisition timeouts, not lock errors.
     *
     * @param name           the name handlers reference
     * @param maxConcurrency permits; must be >= 1
     */
    public static ConsumerExecutor virtual(String name, int maxConcurrency) {
        var factory = Thread.ofVirtual().name("evento-consumer-" + name + "-", 0).factory();
        return new BoundedConsumerExecutor(name, maxConcurrency,
                Executors.newThreadPerTaskExecutor(factory));
    }

    /**
     * Fixed platform-thread pool of {@code threads} workers, admitting at most that many
     * tasks at a time.
     *
     * <p>Use this instead of {@link #virtual} for CPU-bound handlers, and as the escape
     * hatch for handlers holding a JDBC transaction if the driver or connection pool turns
     * out to pin carrier threads — pinning would silently collapse the concurrency a
     * virtual executor is supposed to provide.
     *
     * @param name    the name handlers reference
     * @param threads pool size and permit count; must be >= 1
     */
    public static ConsumerExecutor pooled(String name, int threads) {
        if (threads < 1) throw new IllegalArgumentException("threads must be >= 1, got " + threads);
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        return new BoundedConsumerExecutor(name, threads,
                Executors.newFixedThreadPool(threads,
                        r -> {
                            var t = new Thread(r, "evento-consumer-" + name + "-" + counter.getAndIncrement());
                            t.setDaemon(true);
                            return t;
                        }));
    }

    /**
     * Adapt an existing {@link Executor} with no capacity bound.
     *
     * <p>Compatibility shim for the legacy observer wiring — see
     * {@link com.evento.common.messaging.consumer.impl.UnboundedConsumerExecutor}. Prefer
     * {@link #virtual(String, int)}, which gives real backpressure.
     */
    public static ConsumerExecutor unbounded(String name, Executor delegate) {
        return new UnboundedConsumerExecutor(name, delegate);
    }
}
