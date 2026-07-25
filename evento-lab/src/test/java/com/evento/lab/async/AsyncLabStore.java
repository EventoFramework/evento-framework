package com.evento.lab.async;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cross-thread assertion surface for the async-consumer ITs. Every field is concurrent:
 * the whole point of the feature under test is that handlers run on many threads at once.
 */
public final class AsyncLabStore {

    private AsyncLabStore() {
    }

    /** Order ids applied by the async handler, in completion order. */
    public static final List<String> applied = new CopyOnWriteArrayList<>();

    /** Thread names observed per phase, keyed by {@code orderId + ":" + phase}. */
    public static final Map<String, String> threadNames = new ConcurrentHashMap<>();

    /** Value the interceptor bound to its ThreadLocal, as read back inside the handler. */
    public static final Map<String, String> transactionSeenByHandler = new ConcurrentHashMap<>();

    /** Live and peak concurrency of the async handler. */
    public static final AtomicInteger concurrent = new AtomicInteger();
    public static final AtomicInteger peakConcurrent = new AtomicInteger();

    /** Per-order handler delay, so a test can make handlers slow enough to overlap. */
    public static volatile long handlerDelayMillis = 0L;

    /** When set, the handler blocks until released — used to hold tasks in flight. */
    public static volatile CountDownLatch gate = null;

    /** Order ids whose handler should throw. */
    public static final List<String> failFor = new CopyOnWriteArrayList<>();

    /** Applied sequence numbers per aggregate, for per-key ordering assertions. */
    public static final Map<String, List<Long>> appliedPerAggregate = new ConcurrentHashMap<>();

    public static void recordOrder(String aggregateId, long sequenceNumber) {
        appliedPerAggregate
                .computeIfAbsent(aggregateId, k -> new CopyOnWriteArrayList<>())
                .add(sequenceNumber);
    }

    public static void enter() {
        int now = concurrent.incrementAndGet();
        peakConcurrent.updateAndGet(p -> Math.max(p, now));
    }

    public static void exit() {
        concurrent.decrementAndGet();
    }

    public static void reset() {
        appliedPerAggregate.clear();
        applied.clear();
        threadNames.clear();
        transactionSeenByHandler.clear();
        concurrent.set(0);
        peakConcurrent.set(0);
        handlerDelayMillis = 0L;
        gate = null;
        failFor.clear();
    }
}
